#!/usr/bin/env python3
"""Verificador de los dashboards de Grafana de VERyGANA (spec 011).

Solo usa la biblioteca estándar (Python 3.10+). Subcomandos:

  lint <ruta>              reglas estáticas (RN-2): todo selector filtra por ambiente.
  live ...                 corre cada consulta contra el Prometheus local (CA-3).
  render-promtool ...      genera los tests de PromQL a partir de los dashboards.

Se corre siempre con `python3 -B` para no dejar __pycache__ en el árbol.
"""
import argparse
import json
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path

REQUIRED_DASHBOARDS = ("api-jvm.json", "jobs-scheduled.json", "payouts.json", "slo.json")
ENV_QUERY = 'label_values(up{job="verygana-api"}, env)'
ENV_MATCHERS = ('env="$env"', 'env="${env}"')
ACTUATOR_MATCHER = 'uri!~"/actuator/.*"'
DASHBOARD_TAG = "verygana"

# Funciones y operadores de PromQL que el verificador conoce. Una función fuera de
# esta lista se reporta como `unknown-token` (falla cerrado): agregarla es una línea.
FUNCTIONS = {
    "sum", "min", "max", "avg", "count", "group", "stddev", "stdvar", "topk", "bottomk",
    "quantile", "count_values", "rate", "irate", "increase", "delta", "idelta", "deriv",
    "histogram_quantile", "round", "ceil", "floor", "abs", "clamp", "clamp_min", "clamp_max",
    "vector", "scalar", "time", "max_over_time", "min_over_time", "avg_over_time",
    "sum_over_time", "count_over_time", "last_over_time", "label_replace", "label_join",
    "label_values", "absent", "absent_over_time", "sort", "sort_desc", "changes", "resets",
    "predict_linear", "ln", "log2", "log10", "exp", "sqrt", "timestamp", "present_over_time",
}
KEYWORDS = {"and", "or", "unless", "bool", "offset", "by", "without", "on", "ignoring",
            "group_left", "group_right", "inf", "nan"}
GROUPING = re.compile(r"\b(?:by|without|on|ignoring|group_left|group_right)\s*\([^)]*\)")
NUMBER = re.compile(r"\b\d+(?:\.\d+)?(?:[eE][+-]?\d+)?\b")
IDENT = re.compile(r"[A-Za-z_:][A-Za-z0-9_:]*")


@dataclass(frozen=True)
class Violation:
    file: str
    where: str
    ref_id: str
    rule: str
    detail: str

    def format(self):
        return " · ".join((self.file, self.where, self.ref_id, self.rule, self.detail))


# ---------------------------------------------------------------- análisis de PromQL

def scan_expr(expr):
    """Descompone una expresión PromQL sin parser completo.

    Devuelve (selectors, residue): selectors es la lista de (nombre|None, matchers)
    de cada bloque `nombre{...}`; residue es el texto que queda sin literales, bloques,
    rangos ni listas de agrupación, donde se buscan funciones y métricas sueltas.
    """
    selectors = []
    residue = []
    i, n = 0, len(expr)
    while i < n:
        c = expr[i]
        if c == '"':
            j = i + 1
            while j < n and expr[j] != '"':
                j += 2 if expr[j] == "\\" else 1
            residue.append(" ")
            i = j + 1
        elif c == "{":
            j, depth = i + 1, 1
            while j < n and depth:
                if expr[j] == '"':
                    j += 1
                    while j < n and expr[j] != '"':
                        j += 2 if expr[j] == "\\" else 1
                elif expr[j] == "{":
                    depth += 1
                elif expr[j] == "}":
                    depth -= 1
                j += 1
            body = expr[i + 1:j - 1]
            text = "".join(residue)
            m = re.search(r"([A-Za-z_:][A-Za-z0-9_:]*)$", text)
            name = m.group(1) if m else None
            if m:
                residue = [text[:m.start()]]
            selectors.append((name, body))
            residue.append(" ")
            i = j
        elif c == "[":
            j = expr.find("]", i)
            residue.append(" ")
            i = n if j < 0 else j + 1
        else:
            residue.append(c)
            i += 1
    text = "".join(residue)
    text = GROUPING.sub(" ", text)
    text = re.sub(r"\$\{?\w+\}?", " ", text)
    text = NUMBER.sub(" ", text)
    return selectors, text


def residue_tokens(text):
    """Identificadores del residuo, con True si van seguidos de `(` (función)."""
    for m in IDENT.finditer(text):
        yield m.group(0), text[m.end():].lstrip().startswith("(")


def check_expr(expr):
    """Violaciones (regla, detalle) de una expresión; no conoce archivo ni panel."""
    found = []
    selectors, residue = scan_expr(expr)
    for name, body in selectors:
        label = name or "{...}"
        if not any(m in body for m in ENV_MATCHERS):
            found.append(("env-filter", f"selector {label} sin env=\"$env\""))
        if name and name.startswith("http_server_requests") and ACTUATOR_MATCHER not in body:
            found.append(("actuator-excluded", f"{label} sin {ACTUATOR_MATCHER}"))
    for token, is_call in residue_tokens(residue):
        if token in KEYWORDS:
            continue
        if is_call:
            if token not in FUNCTIONS:
                found.append(("unknown-token", f"función desconocida {token}()"))
        else:
            found.append(("env-filter", f"métrica suelta {token} sin selector con env=\"$env\""))
    found.extend(check_increase(expr))
    return found


def increase_calls(expr):
    """Por cada `increase(`: (rango, dentro_de_round). Ignora literales entre comillas."""
    calls = []
    stack = []  # nombre de la función de cada paréntesis abierto
    i, n = 0, len(expr)
    while i < n:
        c = expr[i]
        if c == '"':
            j = i + 1
            while j < n and expr[j] != '"':
                j += 2 if expr[j] == "\\" else 1
            i = j + 1
            continue
        if c == "(":
            m = re.search(r"([A-Za-z_:][A-Za-z0-9_:]*)\s*$", expr[:i])
            name = m.group(1) if m else None
            stack.append(name)
            if name == "increase":
                rng = re.match(r"[^\[]*\[([^\]]*)\]", expr[i + 1:])
                calls.append((rng.group(1).strip() if rng else "", "round" in stack[:-1]))
        elif c == ")" and stack:
            stack.pop()
        i += 1
    return calls


def check_increase(expr):
    found = []
    for rng, _ in increase_calls(expr):
        if re.search(r"\$\{?__(rate_interval|interval)\}?", rng):
            found.append(("no-zoom-counts", f"increase con rango {rng}: cambia con el zoom"))
    return found


# ---------------------------------------------------------------- recorrido del JSON

def walk_panels(panels):
    for panel in panels or []:
        yield panel
        yield from walk_panels(panel.get("panels"))


def lint_file(path):
    path = Path(path)
    name = path.name
    try:
        dash = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as err:
        return [Violation(name, "-", "-", "json", f"no se pudo leer o parsear: {err}")]
    out = []

    def add(where, ref, rule, detail):
        out.append(Violation(name, where, ref, rule, detail))

    is_payouts = name == "payouts.json" or dash.get("uid") == "verygana-payouts"

    # env-variable
    variables = (dash.get("templating") or {}).get("list") or []
    env_var = next((v for v in variables if v.get("name") == "env"), None)
    if env_var is None:
        add("variable env", "-", "env-variable", "no existe la variable env")
    else:
        ds = env_var.get("datasource")
        ds_uid = ds.get("uid") if isinstance(ds, dict) else ds
        query = env_var.get("query")
        query = query.get("query") if isinstance(query, dict) else query
        problems = []
        if env_var.get("type") != "query":
            problems.append("type != query")
        if env_var.get("multi") is not False:
            problems.append("multi debe ser false")
        if env_var.get("includeAll") is not False:
            problems.append("includeAll debe ser false")
        if ds_uid != "${DS_PROM}":
            problems.append("datasource != ${DS_PROM}")
        if query != ENV_QUERY:
            problems.append(f"consulta != {ENV_QUERY}")
        for p in problems:
            add("variable env", "-", "env-variable", p)

    # env-filter / actuator-excluded / no-zoom-counts / unknown-token en variables
    for var in variables:
        if var.get("type") == "query" and var.get("name") != "env":
            q = var.get("query")
            q = q.get("query") if isinstance(q, dict) else q
            for rule, detail in check_expr(q or ""):
                add(f"variable {var.get('name')}", "-", rule, detail)

    # paneles (recursivo: las filas colapsadas guardan sus paneles en row.panels)
    for panel in walk_panels(dash.get("panels")):
        title = panel.get("title", "(sin título)")
        panel_exprs = [(t.get("refId", "-"), t.get("expr", "")) for t in panel.get("targets") or []]
        for ref, expr in panel_exprs:
            for rule, detail in check_expr(expr):
                add(title, ref, rule, detail)
        if is_payouts:
            for ref, expr in panel_exprs:
                calls = increase_calls(expr)
                for rng, in_round in calls:
                    if not in_round:
                        add(title, ref, "payout-daily-counts", "increase sin round(...)")
                    if panel.get("type") == "timeseries" and rng != "1d":
                        add(title, ref, "payout-daily-counts", f"increase con rango [{rng}], debe ser [1d]")
                if calls and panel.get("type") == "timeseries" and panel.get("interval") != "1d":
                    add(title, ref, "payout-daily-counts", 'el panel debe tener interval: "1d"')

    # shared-crosshair
    if dash.get("graphTooltip") != 1:
        add("dashboard", "-", "shared-crosshair", "graphTooltip debe ser 1")

    # links
    links = dash.get("links") or []
    ok_link = any(
        l.get("type") == "dashboards" and l.get("tags") == [DASHBOARD_TAG]
        and l.get("includeVars") is True and l.get("keepTime") is True
        for l in links
    )
    if not ok_link:
        add("dashboard", "-", "links",
            'falta enlace type=dashboards, tags=["verygana"], includeVars y keepTime en true')
    if DASHBOARD_TAG not in (dash.get("tags") or []):
        add("dashboard", "-", "links", "falta la etiqueta verygana")

    # annotations
    annotations = (dash.get("annotations") or {}).get("list") or []
    prom = [a for a in annotations if not a.get("builtIn") and a.get("expr")]
    if not prom:
        add("anotaciones", "-", "annotations", "falta la anotación de alertas de Prometheus")
    for a in annotations:
        label = a.get("name", "(sin nombre)")
        if a.get("builtIn") and a.get("enable") is not False:
            add(label, "-", "annotations", "la anotación integrada no filtra por ambiente: enable debe ser false")
        elif a.get("expr") is not None:
            for rule, detail in check_expr(a["expr"]):
                add(label, "-", rule, detail)
    return out


def lint_path(path):
    path = Path(path)
    if path.is_file():
        return lint_file(path)
    violations = []
    for f in sorted(path.glob("*.json")):
        violations.extend(lint_file(f))
    present = {f.name for f in path.glob("*.json")}
    for required in REQUIRED_DASHBOARDS:
        if required not in present:
            violations.append(Violation(required, "-", "-", "required-dashboards", "el dashboard no existe"))
    return violations


def cmd_lint(args):
    violations = lint_path(args.path)
    for v in violations:
        print(v.format())
    if violations:
        print(f"{len(violations)} violación(es)", file=sys.stderr)
        return 1
    return 0


# ---------------------------------------------------------------- live (CA-3)

QUERY_TIMEOUT_SECONDS = 10
DEFAULT_DASHBOARDS = Path(__file__).resolve().parent / "grafana" / "dashboards"


def collect_queries(dashboards_dir):
    """(archivo, título, refId, expr) de cada consulta de panel, recursivo."""
    queries = []
    for f in sorted(Path(dashboards_dir).glob("*.json")):
        dash = json.loads(f.read_text(encoding="utf-8"))
        for panel in walk_panels(dash.get("panels")):
            for t in panel.get("targets") or []:
                queries.append((f.name, panel.get("title", "(sin título)"), t.get("refId", "-"), t.get("expr", "")))
    return queries


def substitute(expr, env):
    """Variables de Grafana por valores fijos para una consulta instantánea."""
    for var, value in (("env", env), ("__range", "6h"), ("__rate_interval", "1m"), ("__interval", "1d")):
        expr = expr.replace("${%s}" % var, value).replace("$" + var, value)
    return expr


class QueryRejected(Exception):
    """Prometheus respondió y dijo que la consulta es inválida (bug del dashboard)."""


class Unreachable(Exception):
    """No se pudo hablar con Prometheus (conexión rehusada, timeout, respuesta ilegible)."""


def run_query(prometheus, expr):
    """True si la consulta devuelve al menos una serie."""
    url = prometheus.rstrip("/") + "/api/v1/query?" + urllib.parse.urlencode({"query": expr})
    try:
        with urllib.request.urlopen(url, timeout=QUERY_TIMEOUT_SECONDS) as resp:
            body = json.load(resp)
    except urllib.error.HTTPError as err:
        # Prometheus contesta 400 con un JSON status=error cuando la consulta no parsea.
        try:
            body = json.load(err)
        except ValueError:
            raise Unreachable(f"HTTP {err.code} sin cuerpo JSON") from err
    except (urllib.error.URLError, TimeoutError, OSError) as err:
        raise Unreachable(str(err)) from err
    except ValueError as err:
        raise Unreachable("respuesta que no es JSON") from err
    if body.get("status") != "success":
        raise QueryRejected(body.get("error", "error desconocido"))
    return bool(body["data"]["result"])


def cmd_live(args):
    results = {}
    rejected = []
    try:
        for file, title, ref, expr in collect_queries(args.dashboards):
            key = f"{file}|{title}|{ref}"
            try:
                results[key] = run_query(args.prometheus, substitute(expr, args.env))
            except QueryRejected as err:
                rejected.append(f"{key} · consulta inválida: {err} · {expr}")
    except Unreachable as err:
        print(f"No se pudo hablar con Prometheus en {args.prometheus}: {err}. "
              "Levanta el stack con `docker compose up -d prometheus grafana`.", file=sys.stderr)
        return 2
    for line in rejected:
        print(line)
    with_data = sum(results.values())
    print(f"{with_data} de {len(results)} consultas devuelven datos")
    if args.save:
        Path(args.save).write_text(json.dumps(results, indent=2, ensure_ascii=False), encoding="utf-8")
        print(f"línea base guardada en {args.save}")
    lost = []
    if args.compare:
        baseline = json.loads(Path(args.compare).read_text(encoding="utf-8"))
        lost = sorted(k for k, had in baseline.items() if had and not results.get(k, False))
        for key in lost:
            print(f"perdió datos: {key}")
    return 1 if rejected or lost else 0


# ---------------------------------------------------------------- render-promtool

class CaseError(Exception):
    """Un caso de promql-cases.json no se puede armar (panel inexistente, variable sin valor)."""


def find_expr(dashboards_dir, case):
    path = Path(dashboards_dir) / case["dashboard"]
    if not path.is_file():
        raise CaseError(f"{case['name']}: no existe el dashboard {case['dashboard']}")
    dash = json.loads(path.read_text(encoding="utf-8"))
    for panel in walk_panels(dash.get("panels")):
        if panel.get("title") != case["panel"]:
            continue
        for t in panel.get("targets") or []:
            if t.get("refId") == case["refId"]:
                return t["expr"]
    raise CaseError(f"{case['name']}: panel no encontrado ({case['dashboard']} · {case['panel']} · {case['refId']})")


def render_case(dashboards_dir, case):
    expr = find_expr(dashboards_dir, case)
    # Las más largas primero: `$__rate_interval` antes que cualquier prefijo suyo.
    for var, value in sorted((case.get("vars") or {}).items(), key=lambda kv: -len(kv[0])):
        name = var.lstrip("$")
        expr = expr.replace("${%s}" % name, value).replace("$" + name, value)
    if "$" in expr:
        raise CaseError(f"{case['name']}: queda una variable sin valor en la expresión: {expr}")
    if case.get("wrap"):
        expr = case["wrap"] % expr
    return {
        "interval": case.get("interval", "1m"),
        "input_series": case["input_series"],
        "promql_expr_test": [{
            "expr": expr,
            "eval_time": case["eval_time"],
            "exp_samples": case.get("exp_samples", []),
        }],
    }


def cmd_render_promtool(args):
    cases = json.loads(Path(args.cases).read_text(encoding="utf-8"))["cases"]
    try:
        tests = [render_case(args.dashboards, c) for c in cases]
    except CaseError as err:
        print(f"render-promtool: {err}", file=sys.stderr)
        return 1
    out = Path(args.out_dir)
    out.mkdir(parents=True, exist_ok=True)
    # JSON es YAML válido: no hace falta PyYAML.
    doc = {"rule_files": ["/r/empty.rules.yml"], "evaluation_interval": "1m", "tests": tests}
    (out / "dashboards.test.yml").write_text(json.dumps(doc, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"{len(tests)} caso(s) escritos en {out / 'dashboards.test.yml'}")
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = parser.add_subparsers(dest="command", required=True)
    p_lint = sub.add_parser("lint", help="reglas estáticas")
    p_lint.add_argument("path", help="carpeta de dashboards o un archivo .json")
    p_lint.set_defaults(func=cmd_lint)
    p_live = sub.add_parser("live", help="consultas contra el Prometheus local (CA-3)")
    p_live.add_argument("--prometheus", default="http://localhost:9090")
    p_live.add_argument("--env", required=True)
    p_live.add_argument("--dashboards", default=str(DEFAULT_DASHBOARDS))
    p_live.add_argument("--save", help="guarda qué consultas tienen datos (línea base)")
    p_live.add_argument("--compare", help="falla si una consulta con datos en la línea base ya no los tiene")
    p_live.set_defaults(func=cmd_live)
    p_render = sub.add_parser("render-promtool", help="genera el test de promtool desde los dashboards")
    p_render.add_argument("cases", help="monitoring/tests/promql-cases.json")
    p_render.add_argument("out_dir")
    p_render.add_argument("--dashboards", default=str(DEFAULT_DASHBOARDS))
    p_render.set_defaults(func=cmd_render_promtool)
    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
