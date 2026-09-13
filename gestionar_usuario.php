<?php
session_start();
include("conexion.php");
header('Content-Type: application/json');

if (!isset($_SESSION['usuario_id']) || $_SESSION['rol'] !== 'administrador') {
    http_response_code(401);
    echo json_encode(['ok' => false, 'error' => 'Debes iniciar sesión como administrador.']);
    exit();
}

$admin_id   = (int) $_SESSION['usuario_id'];
$usuario_id = (int) ($_POST['usuario_id'] ?? 0);
$activo     = isset($_POST['activo']) ? (int) $_POST['activo'] : null;

if ($usuario_id <= 0 || !in_array($activo, [0, 1], true)) {
    echo json_encode(['ok' => false, 'error' => 'Datos inválidos.']);
    exit();
}

if ($usuario_id === $admin_id) {
    echo json_encode(['ok' => false, 'error' => 'No puedes suspender tu propia cuenta.']);
    exit();
}

$check = $conn->prepare("SELECT rol FROM usuarios WHERE id = ?");
$check->bind_param("i", $usuario_id);
$check->execute();
$u = $check->get_result()->fetch_assoc();

if (!$u) {
    echo json_encode(['ok' => false, 'error' => 'Usuario no encontrado.']);
    exit();
}
if ($u['rol'] === 'administrador') {
    echo json_encode(['ok' => false, 'error' => 'No puedes suspender a otro administrador.']);
    exit();
}

$stmt = $conn->prepare("UPDATE usuarios SET activo = ? WHERE id = ?");
$stmt->bind_param("ii", $activo, $usuario_id);
$ok = $stmt->execute();

echo json_encode(['ok' => $ok, 'activo' => $activo]);