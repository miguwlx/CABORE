<?php
/**
 * gestionar_tienda.php — Permite al administrador suspender o reactivar
 * una tienda completa desde la pestaña "Tiendas" del panel de admin.
 */
session_start();
include("conexion.php");
header('Content-Type: application/json');

if (!isset($_SESSION['usuario_id']) || $_SESSION['rol'] !== 'administrador') {
    http_response_code(401);
    echo json_encode(['ok' => false, 'error' => 'Debes iniciar sesión como administrador.']);
    exit();
}

$tienda_id = (int) ($_POST['tienda_id'] ?? 0);
$activo    = isset($_POST['activo']) ? (int) $_POST['activo'] : null;
$motivo    = trim($_POST['motivo'] ?? '');

if ($tienda_id <= 0 || !in_array($activo, [0, 1], true)) {
    echo json_encode(['ok' => false, 'error' => 'Datos inválidos.']);
    exit();
}

if ($activo === 0 && $motivo === '') {
    echo json_encode(['ok' => false, 'error' => 'Debes indicar un motivo de suspensión.']);
    exit();
}

$motivo_guardado = $activo === 0 ? $motivo : null;

$stmt = $conn->prepare("UPDATE tiendas SET activo = ?, motivo_suspension = ? WHERE id = ?");
$stmt->bind_param("isi", $activo, $motivo_guardado, $tienda_id);
$ok = $stmt->execute();

echo json_encode(['ok' => $ok, 'activo' => $activo]);