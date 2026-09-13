<?php
/**
 * reporte_csv.php — Exporta a CSV el reporte de ventas/pedidos con los
 * mismos filtros usados en la pestaña "Reportes" del panel de admin.
 */
session_start();
include("conexion.php");

if (!isset($_SESSION['usuario_id']) || $_SESSION['rol'] !== 'administrador') {
    http_response_code(401);
    die('Acceso no autorizado.');
}

$fecha_desde = $_GET['desde'] ?? '';
$fecha_hasta = $_GET['hasta'] ?? '';
$estado      = $_GET['estado'] ?? '';
$tienda_id   = (int) ($_GET['tienda_id'] ?? 0);

$where  = ["1=1"];
$params = [];
$types  = "";

if ($fecha_desde !== '') {
    $where[]  = "p.creado_en >= ?";
    $params[] = $fecha_desde . " 00:00:00";
    $types   .= "s";
}
if ($fecha_hasta !== '') {
    $where[]  = "p.creado_en <= ?";
    $params[] = $fecha_hasta . " 23:59:59";
    $types   .= "s";
}
if ($estado !== '' && in_array($estado, ['pendiente','confirmado','enviado','entregado','cancelado'], true)) {
    $where[]  = "p.estado = ?";
    $params[] = $estado;
    $types   .= "s";
}
if ($tienda_id > 0) {
    $where[]  = "t.id = ?";
    $params[] = $tienda_id;
    $types   .= "i";
}

$sql = "
    SELECT p.id AS pedido_id, p.creado_en, p.estado, p.total, p.direccion,
           u.nombre AS cliente, u.correo AS cliente_correo,
           GROUP_CONCAT(DISTINCT t.nombre SEPARATOR ' | ') AS tiendas
    FROM pedidos p
    JOIN usuarios u ON u.id = p.usuario_id
    JOIN pedido_items pi ON pi.pedido_id = p.id
    JOIN productos pr ON pr.id = pi.producto_id
    JOIN tiendas t ON t.id = pr.tienda_id
    WHERE " . implode(' AND ', $where) . "
    GROUP BY p.id
    ORDER BY p.creado_en DESC
";

$stmt = $conn->prepare($sql);
if ($params) {
    $stmt->bind_param($types, ...$params);
}
$stmt->execute();
$res = $stmt->get_result();

header('Content-Type: text/csv; charset=utf-8');
header('Content-Disposition: attachment; filename="reporte_ventas_' . date('Y-m-d_His') . '.csv"');

$out = fopen('php://output', 'w');
// BOM para que Excel abra bien los acentos en UTF-8
fwrite($out, "\xEF\xBB\xBF");
fputcsv($out, ['Pedido', 'Fecha', 'Cliente', 'Correo', 'Tienda(s)', 'Estado', 'Total', 'Dirección']);

while ($row = $res->fetch_assoc()) {
    fputcsv($out, [
        'FAC-' . str_pad($row['pedido_id'], 6, '0', STR_PAD_LEFT),
        $row['creado_en'],
        $row['cliente'],
        $row['cliente_correo'],
        $row['tiendas'],
        $row['estado'],
        $row['total'],
        $row['direccion'],
    ]);
}
fclose($out);
exit();