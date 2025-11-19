-- ============================================
-- Script para poblar datos de Colombia
-- Ejecutar después de crear las tablas
-- ============================================

-- DEPARTAMENTOS DE COLOMBIA (33 departamentos)
INSERT INTO departments (code, name) VALUES
('05', 'Antioquia'),
('08', 'Atlántico'),
('11', 'Bogotá D.C.'),
('13', 'Bolívar'),
('15', 'Boyacá'),
('17', 'Caldas'),
('18', 'Caquetá'),
('19', 'Cauca'),
('20', 'Cesar'),
('23', 'Córdoba'),
('25', 'Cundinamarca'),
('27', 'Chocó'),
('41', 'Huila'),
('44', 'La Guajira'),
('47', 'Magdalena'),
('50', 'Meta'),
('52', 'Nariño'),
('54', 'Norte de Santander'),
('63', 'Quindío'),
('66', 'Risaralda'),
('68', 'Santander'),
('70', 'Sucre'),
('73', 'Tolima'),
('76', 'Valle del Cauca'),
('81', 'Arauca'),
('85', 'Casanare'),
('86', 'Putumayo'),
('88', 'San Andrés y Providencia'),
('91', 'Amazonas'),
('94', 'Guainía'),
('95', 'Guaviare'),
('97', 'Vaupés'),
('99', 'Vichada')
ON CONFLICT (code) DO NOTHING;

-- MUNICIPIOS PRINCIPALES (Los más poblados de cada departamento)

-- Antioquia
INSERT INTO municipalities (code, name, department_code) VALUES
('05001', 'Medellín', '05'),
('05088', 'Bello', '05'),
('05360', 'Itagüí', '05'),
('05266', 'Envigado', '05'),
('05631', 'Rionegro', '05')
ON CONFLICT (code) DO NOTHING;

-- Atlántico
INSERT INTO municipalities (code, name, department_code) VALUES
('08001', 'Barranquilla', '08'),
('08758', 'Soledad', '08'),
('08520', 'Malambo', '08')
ON CONFLICT (code) DO NOTHING;

-- Bogotá D.C.
INSERT INTO municipalities (code, name, department_code) VALUES
('11001', 'Bogotá', '11')
ON CONFLICT (code) DO NOTHING;

-- Bolívar
INSERT INTO municipalities (code, name, department_code) VALUES
('13001', 'Cartagena', '13'),
('13580', 'Magangué', '13')
ON CONFLICT (code) DO NOTHING;

-- Boyacá
INSERT INTO municipalities (code, name, department_code) VALUES
('15001', 'Tunja', '15'),
('15238', 'Duitama', '15'),
('15759', 'Sogamoso', '15')
ON CONFLICT (code) DO NOTHING;

-- Caldas
INSERT INTO municipalities (code, name, department_code) VALUES
('17001', 'Manizales', '17'),
('17380', 'La Dorada', '17')
ON CONFLICT (code) DO NOTHING;

-- Cauca
INSERT INTO municipalities (code, name, department_code) VALUES
('19001', 'Popayán', '19'),
('19698', 'Santander de Quilichao', '19')
ON CONFLICT (code) DO NOTHING;

-- Cesar
INSERT INTO municipalities (code, name, department_code) VALUES
('20001', 'Valledupar', '20'),
('20750', 'Aguachica', '20')
ON CONFLICT (code) DO NOTHING;

-- Córdoba
INSERT INTO municipalities (code, name, department_code) VALUES
('23001', 'Montería', '23'),
('23670', 'Sahagún', '23')
ON CONFLICT (code) DO NOTHING;

-- Cundinamarca
INSERT INTO municipalities (code, name, department_code) VALUES
('25899', 'Soacha', '25'),
('25123', 'Chía', '25'),
('25290', 'Funza', '25'),
('25473', 'Madrid', '25'),
('25506', 'Mosquera', '25'),
('25754', 'Sibaté', '25'),
('25269', 'Facatativá', '25'),
('25873', 'Zipaquirá', '25')
ON CONFLICT (code) DO NOTHING;

-- Huila
INSERT INTO municipalities (code, name, department_code) VALUES
('41001', 'Neiva', '41'),
('41132', 'Campoalegre', '41'),
('41244', 'Garzón', '41')
ON CONFLICT (code) DO NOTHING;

-- La Guajira
INSERT INTO municipalities (code, name, department_code) VALUES
('44001', 'Riohacha', '44'),
('44430', 'Maicao', '44')
ON CONFLICT (code) DO NOTHING;

-- Magdalena
INSERT INTO municipalities (code, name, department_code) VALUES
('47001', 'Santa Marta', '47'),
('47189', 'Ciénaga', '47')
ON CONFLICT (code) DO NOTHING;

-- Meta
INSERT INTO municipalities (code, name, department_code) VALUES
('50001', 'Villavicencio', '50'),
('50006', 'Acacías', '50')
ON CONFLICT (code) DO NOTHING;

-- Nariño
INSERT INTO municipalities (code, name, department_code) VALUES
('52001', 'Pasto', '52'),
('52356', 'Ipiales', '52'),
('52835', 'Tumaco', '52')
ON CONFLICT (code) DO NOTHING;

-- Norte de Santander
INSERT INTO municipalities (code, name, department_code) VALUES
('54001', 'Cúcuta', '54'),
('54874', 'Villa del Rosario', '54'),
('54498', 'Los Patios', '54'),
('54599', 'Ocaña', '54')
ON CONFLICT (code) DO NOTHING;

-- Quindío (TODOS los municipios)
INSERT INTO municipalities (code, name, department_code) VALUES
('63001', 'Armenia', '63'),
('63111', 'Buenavista', '63'),
('63130', 'Calarcá', '63'),
('63190', 'Circasia', '63'),
('63212', 'Córdoba', '63'),
('63272', 'Filandia', '63'),
('63302', 'Génova', '63'),
('63401', 'La Tebaida', '63'),
('63470', 'Montenegro', '63'),
('63548', 'Pijao', '63'),
('63594', 'Quimbaya', '63'),
('63690', 'Salento', '63')
ON CONFLICT (code) DO NOTHING;

-- Risaralda
INSERT INTO municipalities (code, name, department_code) VALUES
('66001', 'Pereira', '66'),
('66170', 'Dosquebradas', '66'),
('66440', 'La Virginia', '66'),
('66682', 'Santa Rosa de Cabal', '66')
ON CONFLICT (code) DO NOTHING;

-- Santander
INSERT INTO municipalities (code, name, department_code) VALUES
('68001', 'Bucaramanga', '68'),
('68276', 'Floridablanca', '68'),
('68307', 'Girón', '68'),
('68547', 'Piedecuesta', '68'),
('68081', 'Barrancabermeja', '68')
ON CONFLICT (code) DO NOTHING;

-- Sucre
INSERT INTO municipalities (code, name, department_code) VALUES
('70001', 'Sincelejo', '70')
ON CONFLICT (code) DO NOTHING;

-- Tolima
INSERT INTO municipalities (code, name, department_code) VALUES
('73001', 'Ibagué', '73'),
('73268', 'Espinal', '73')
ON CONFLICT (code) DO NOTHING;

-- Valle del Cauca
INSERT INTO municipalities (code, name, department_code) VALUES
('76001', 'Cali', '76'),
('76111', 'Buenaventura', '76'),
('76109', 'Buga', '76'),
('76147', 'Cartago', '76'),
('76834', 'Tuluá', '76'),
('76520', 'Palmira', '76'),
('76892', 'Yumbo', '76')
ON CONFLICT (code) DO NOTHING;

-- Arauca
INSERT INTO municipalities (code, name, department_code) VALUES
('81001', 'Arauca', '81')
ON CONFLICT (code) DO NOTHING;

-- Casanare
INSERT INTO municipalities (code, name, department_code) VALUES
('85001', 'Yopal', '85')
ON CONFLICT (code) DO NOTHING;

-- Putumayo
INSERT INTO municipalities (code, name, department_code) VALUES
('86001', 'Mocoa', '86')
ON CONFLICT (code) DO NOTHING;

-- San Andrés y Providencia
INSERT INTO municipalities (code, name, department_code) VALUES
('88001', 'San Andrés', '88')
ON CONFLICT (code) DO NOTHING;

-- CATEGORÍAS DE EJEMPLO
INSERT INTO categories (name, description) VALUES
('Tecnología', 'Productos y servicios tecnológicos'),
('Moda', 'Ropa, accesorios y tendencias'),
('Alimentos', 'Restaurantes, comida y bebidas'),
('Deportes', 'Actividades deportivas y fitness'),
('Educación', 'Cursos, capacitaciones y formación'),
('Entretenimiento', 'Cine, música y eventos'),
('Salud', 'Servicios médicos y bienestar'),
('Hogar', 'Decoración y artículos para el hogar'),
('Viajes', 'Turismo y destinos'),
('Automotriz', 'Vehículos y servicios relacionados'),
('Finanzas', 'Servicios financieros y bancarios'),
('Belleza', 'Cosméticos y cuidado personal')
ON CONFLICT DO NOTHING;

-- ============================================
-- VERIFICACIÓN
-- ============================================

-- Verificar departamentos insertados
SELECT COUNT(*) as total_departamentos FROM departments;

-- Verificar municipios insertados
SELECT d.name as departamento, COUNT(m.code) as total_municipios
FROM departments d
LEFT JOIN municipalities m ON m.department_code = d.code
GROUP BY d.code, d.name
ORDER BY d.name;

-- Verificar categorías
SELECT * FROM categories ORDER BY name;