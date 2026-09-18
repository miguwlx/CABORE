<?php
session_start();
include("conexion.php");
header('Content-Type: application/json');

if (!isset($_SESSION['usuario_id']) || $_SESSION['rol'] !== 'administrador') {
    http_response_code(401);
    echo json_encode(['ok' => false, 'error' => 'Debes iniciar sesión como administrador.']);
    exit();
}

$accion = $_POST['accion'] ?? '';

if ($accion === 'guardar') {
    $id     = (int) ($_POST['id'] ?? 0);
    $nombre = trim($_POST['nombre'] ?? '');
    $icono  = trim($_POST['icono'] ?? '');

    if ($nombre === '') {
        echo json_encode(['ok' => false, 'error' => 'El nombre es obligatorio.']);
        exit();
    }

    if ($id > 0) {
        $stmt = $conn->prepare("UPDATE categorias SET nombre = ?, icono = ? WHERE id = ?");
        $stmt->bind_param("ssi", $nombre, $icono, $id);
    } else {
        $stmt = $conn->prepare("INSERT INTO categorias (nombre, icono) VALUES (?, ?)");
        $stmt->bind_param("ss", $nombre, $icono);
    }
    $ok = $stmt->execute();
    echo json_encode(['ok' => $ok]);
    exit();
}

if ($accion === 'eliminar') {
    $id = (int) ($_POST['id'] ?? 0);
    if ($id <= 0) { echo json_encode(['ok' => false, 'error' => 'Categoría inválida.']); exit(); }

    $check = $conn->prepare("SELECT COUNT(*) n FROM productos WHERE categoria_id = ?");
    $check->bind_param("i", $id);
    $check->execute();
    $n = (int) $check->get_result()->fetch_assoc()['n'];

    if ($n > 0) {
        echo json_encode(['ok' => false, 'error' => 'No puedes eliminar una categoría con productos asignados.']);
        exit();
    }

    $stmt = $conn->prepare("DELETE FROM categorias WHERE id = ?");
    $stmt->bind_param("i", $id);
    $ok = $stmt->execute();
    echo json_encode(['ok' => $ok]);
    exit();
}

echo json_encode(['ok' => false, 'error' => 'Acción no reconocida.']);