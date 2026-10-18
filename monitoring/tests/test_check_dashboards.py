"""Pruebas del verificador de dashboards (monitoring/check_dashboards.py).

Correr desde la raíz del repo:
    python3 -B -m unittest discover -s monitoring/tests -v
"""
import contextlib
import io
import json
import sys
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

TESTS_DIR = Path(__file__).resolve().parent
MONITORING_DIR = TESTS_DIR.parent
sys.path.insert(0, str(MONITORING_DIR))

import check_dashboards as cd  # noqa: E402

FIXTURES = TESTS_DIR / "fixtures"
DASHBOARDS = MONITORING_DIR / "grafana" / "dashboards"


def rules_of(fixture):
    """Reglas violadas por un fixture (conjunto)."""
    return {v.rule for v in cd.lint_file(FIXTURES / fixture)}


class LintTest(unittest.TestCase):
    def test_valid_fixture_passes(self):
        self.assertEqual([], cd.lint_file(FIXTURES / "valid.json"))

    def test_payout_valid_fixture_passes(self):
        self.assertEqual([], cd.lint_file(FIXTURES / "payout_valid.json"))

    # CA-2
    def test_missing_env_filter_fails(self):
        self.assertIn("env-filter", rules_of("missing_env_filter.json"))

    def test_bare_metric_fails(self):
        self.assertIn("env-filter", rules_of("bare_metric.json"))

    def test_nested_row_without_env_fails(self):
        self.assertIn("env-filter", rules_of("nested_row_without_env.json"))

    def test_annotation_without_env_fails(self):
        self.assertIn("env-filter", rules_of("annotation_without_env.json"))

    def test_unknown_function_fails_closed(self):
        self.assertIn("unknown-token", rules_of("unknown_function.json"))

    # CA-1
    def test_env_multi_fails(self):
        self.assertIn("env-variable", rules_of("env_multi.json"))

    def test_env_include_all_fails(self):
        self.assertIn("env-variable", rules_of("env_include_all.json"))

    # CA-8
    def test_no_shared_crosshair_fails(self):
        self.assertIn("shared-crosshair", rules_of("no_shared_crosshair.json"))

    # CA-11
    def test_link_without_keep_time_fails(self):
        self.assertIn("links", rules_of("link_without_keep_time.json"))

    # CA-9
    def test_http_without_actuator_exclusion_fails(self):
        self.assertIn("actuator-excluded", rules_of("http_without_actuator_exclusion.json"))

    # CA-6
    def test_increase_rate_interval_fails(self):
        self.assertIn("no-zoom-counts", rules_of("increase_rate_interval.json"))

    def test_payout_count_not_rounded_fails(self):
        self.assertIn("payout-daily-counts", rules_of("payout_count_not_rounded.json"))

    def test_payout_without_daily_interval_fails(self):
        self.assertIn("payout-daily-counts", rules_of("payout_without_daily_interval.json"))

    # CA-10
    def test_builtin_annotation_enabled_fails(self):
        self.assertIn("annotations", rules_of("builtin_annotation_enabled.json"))

    def test_violation_line_has_file_panel_refid_rule(self):
        line = cd.lint_file(FIXTURES / "bare_metric.json")[0].format()
        self.assertEqual(5, len(line.split(" · ")))
        self.assertIn("bare_metric.json", line)
        self.assertIn("Peticiones / s", line)

    def test_invalid_json_is_reported(self):
        bad = Path(__file__).resolve().parent / "fixtures" / "__no_existe__.json"
        self.assertEqual({"json"}, {v.rule for v in cd.lint_file(bad)})

    # Metas de los bloques B a E: en rojo hasta T16.
    def test_repo_dashboards_have_no_violations(self):
        violations = cd.lint_path(DASHBOARDS)
        self.assertEqual([], [v.format() for v in violations])

    def test_four_dashboards_present(self):
        names = {p.name for p in DASHBOARDS.glob("*.json")}
        self.assertEqual({"api-jvm.json", "jobs-scheduled.json", "payouts.json", "slo.json"}, names)


class FakePrometheus:
    """Prometheus falso: responde a /api/v1/query según `responder(query)`."""

    def __init__(self, responder):
        self.queries = []
        outer = self

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):  # noqa: N802
                query = parse_qs(urlparse(self.path).query)["query"][0]
                outer.queries.append(query)
                status, body = responder(query)
                data = json.dumps(body).encode()
                self.send_response(status)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)

            def log_message(self, *args):
                pass

        self.server = HTTPServer(("127.0.0.1", 0), Handler)
        self.url = f"http://127.0.0.1:{self.server.server_port}"
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)

    def __enter__(self):
        self.thread.start()
        return self

    def __exit__(self, *exc):
        self.server.shutdown()
        self.server.server_close()


def ok(has_data):
    result = [{"metric": {}, "value": [0, "1"]}] if has_data else []
    return 200, {"status": "success", "data": {"resultType": "vector", "result": result}}


def live(argv):
    """Corre `main(['live', ...])` y devuelve (código, salida estándar, error estándar)."""
    out, err = io.StringIO(), io.StringIO()
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
        code = cd.main(["live", *argv])
    return code, out.getvalue(), err.getvalue()


def write_dashboard(directory, panels):
    (Path(directory) / "d.json").write_text(json.dumps({"uid": "d", "title": "D", "panels": panels}))


def panel(title, expr, ref="A"):
    return {"type": "stat", "title": title, "targets": [{"refId": ref, "expr": expr}]}


class LiveTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.dir = Path(self.tmp.name)

    def test_live_substitutes_variables(self):
        write_dashboard(self.dir, [panel("P", 'sum(increase(x{env="$env"}[$__range]))'),
                                   panel("Q", 'rate(y{env="${env}"}[$__rate_interval])'),
                                   panel("R", 'increase(z[$__interval])')])
        with FakePrometheus(lambda q: ok(True)) as prom:
            code, _, _ = live(["--prometheus", prom.url, "--env", "dev", "--dashboards", str(self.dir)])
        self.assertEqual(0, code)
        self.assertEqual({'sum(increase(x{env="dev"}[6h]))', 'rate(y{env="dev"}[1m])',
                          'increase(z[1d])'}, set(prom.queries))

    def test_live_query_error_exits_1(self):
        write_dashboard(self.dir, [panel("Rota", "sum(")])
        body = {"status": "error", "errorType": "bad_data", "error": "parse error"}
        with FakePrometheus(lambda q: (400, body)) as prom:
            code, out, err = live(["--prometheus", prom.url, "--env", "dev", "--dashboards", str(self.dir)])
        self.assertEqual(1, code)
        self.assertIn("sum(", out + err)

    def test_live_connection_refused_exits_2(self):
        write_dashboard(self.dir, [panel("P", "up")])
        with FakePrometheus(lambda q: ok(True)) as prom:
            url = prom.url
        code, out, err = live(["--prometheus", url, "--env", "dev", "--dashboards", str(self.dir)])
        self.assertEqual(2, code)
        self.assertIn("docker compose up -d prometheus grafana", out + err)

    def test_live_compare_reports_lost_panel(self):
        write_dashboard(self.dir, [panel("Con datos", "a"), panel("Sin datos", "b")])
        baseline = self.dir / "baseline.json"
        with FakePrometheus(lambda q: ok(True)) as prom:
            code, _, _ = live(["--prometheus", prom.url, "--env", "dev", "--dashboards", str(self.dir),
                               "--save", str(baseline)])
        self.assertEqual(0, code)
        self.assertEqual({"d.json|Con datos|A": True, "d.json|Sin datos|A": True},
                         json.loads(baseline.read_text()))
        with FakePrometheus(lambda q: ok(q == "a")) as prom:
            code, out, err = live(["--prometheus", prom.url, "--env", "dev", "--dashboards", str(self.dir),
                                   "--compare", str(baseline)])
        self.assertEqual(1, code)
        self.assertIn("Sin datos", out + err)
        self.assertNotIn("Con datos", out + err)
        with FakePrometheus(lambda q: ok(True)) as prom:
            code, _, _ = live(["--prometheus", prom.url, "--env", "dev", "--dashboards", str(self.dir),
                               "--compare", str(baseline)])
        self.assertEqual(0, code)


class RenderPromtoolTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.dir = Path(self.tmp.name)
        self.dashboards = self.dir / "dashboards"
        self.dashboards.mkdir()
        write_dashboard(self.dashboards, [panel("Otro", "up"), panel("Uptime", 'sum(rate(x{env="$env"}[$__rate_interval]))')])
        (self.dashboards / "d.json").rename(self.dashboards / "api-jvm.json")

    def render(self, cases):
        cases_file = self.dir / "cases.json"
        cases_file.write_text(json.dumps({"cases": cases}))
        out = self.dir / "out"
        code = cd.main(["render-promtool", str(cases_file), str(out), "--dashboards", str(self.dashboards)])
        return code, out / "dashboards.test.yml"

    def case(self, **over):
        base = {"name": "c", "dashboard": "api-jvm.json", "panel": "Uptime", "refId": "A",
                "vars": {"$env": "dev", "$__rate_interval": "5m"}, "interval": "1m",
                "input_series": [{"series": 'x{env="dev"}', "values": "0 60 120"}],
                "eval_time": "2m", "exp_samples": [{"labels": "{}", "value": 1}]}
        base.update(over)
        return base

    def test_render_takes_expr_from_dashboard(self):
        code, out = self.render([self.case()])
        self.assertEqual(0, code)
        doc = json.loads(out.read_text())
        self.assertEqual(["/r/empty.rules.yml"], doc["rule_files"])
        test = doc["tests"][0]
        self.assertEqual('sum(rate(x{env="dev"}[5m]))', test["promql_expr_test"][0]["expr"])
        self.assertEqual("2m", test["promql_expr_test"][0]["eval_time"])
        self.assertEqual([{"series": 'x{env="dev"}', "values": "0 60 120"}], test["input_series"])
        self.assertEqual(1, test["promql_expr_test"][0]["exp_samples"][0]["value"])

    def test_render_fails_when_panel_missing(self):
        code, out = self.render([self.case(panel="No existe")])
        self.assertEqual(1, code)
        self.assertFalse(out.exists())

    def test_render_fails_when_variable_left_unresolved(self):
        code, _ = self.render([self.case(vars={"$env": "dev"})])
        self.assertEqual(1, code)

    def test_render_wrap(self):
        _, out = self.render([self.case(wrap="round(%s * 1e6)")])
        expr = json.loads(out.read_text())["tests"][0]["promql_expr_test"][0]["expr"]
        self.assertEqual('round(sum(rate(x{env="dev"}[5m])) * 1e6)', expr)


if __name__ == "__main__":
    unittest.main()
