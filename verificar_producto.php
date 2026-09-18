<?php
/**
 * verificar_producto.php — Permite al administrador aprobar o rechazar
 * un producto subido por un emprendedor, desde la pestaña "Verificación"
 * del panel de admin.
 */
session_start();
include("conexion.php");

header('Content-Type: application/json');

if (!isset($_SESSION['usuario_id']) || $_SESSION['rol'] !== 'administrador') {
    http_response_code(401);
    echo json_encode(['ok' => false, 'error' => 'Debes iniciar sesión como administrador.']);
    exit();
}

$admin_id     = (int) $_SESSION['usuario_id'];
$producto_id  = (int) ($_POST['producto_id'] ?? 0);
$accion       = $_POST['accion'] ?? '';
$motivo       = trim($_POST['motivo'] ?? '');

if ($producto_id <= 0 || !in_array($accion, ['aprobar', 'rechazar'], true)) {
    echo json_encode(['ok' => false, 'error' => 'Datos inválidos.']);
    exit();
}

if ($accion === 'rechazar' && $motivo === '') {
    echo json_encode(['ok' => false, 'error' => 'Debes indicar un motivo de rechazo.']);
    exit();
}

$estado = $accion === 'aprobar' ? 'aprobado' : 'rechazado';
$motivo_guardado = $accion === 'rechazar' ? $motivo : null;

$stmt = $conn->prepare(
    "UPDATE productos
        SET estado_verificacion = ?, motivo_rechazo = ?, revisado_por = ?, revisado_en = NOW()
      WHERE id = ?"
);
$stmt->bind_param("ssii", $estado, $motivo_guardado, $admin_id, $producto_id);
$ok = $stmt->execute();

if (!$ok) {
    echo json_encode(['ok' => false, 'error' => 'No se pudo actualizar el producto.']);
    exit();
}

echo json_encode(['ok' => true, 'estado' => $estado]);