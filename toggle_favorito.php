<?php
session_start();
require_once 'conexion.php';

header('Content-Type: application/json');

if (!isset($_SESSION['usuario_id']) || $_SESSION['rol'] !== 'cliente') {
    echo json_encode(['ok' => false, 'msg' => 'No autorizado']);
    exit;
}

$usuario_id  = $_SESSION['usuario_id'];
$producto_id = intval($_POST['producto_id'] ?? 0);

if (!$producto_id) {
    echo json_encode(['ok' => false, 'msg' => 'Producto inválido']);
    exit;
}

// ¿Ya existe en favoritos?
$stmt = $pdo->prepare("SELECT id FROM favoritos WHERE usuario_id = ? AND producto_id = ?");
$stmt->execute([$usuario_id, $producto_id]);
$existe = $stmt->fetch();

if ($existe) {
    // Quitar
    $pdo->prepare("DELETE FROM favoritos WHERE usuario_id = ? AND producto_id = ?")
        ->execute([$usuario_id, $producto_id]);
    echo json_encode(['ok' => true, 'accion' => 'eliminado']);
} else {
    // Agregar
    $pdo->prepare("INSERT INTO favoritos (usuario_id, producto_id) VALUES (?, ?)")
        ->execute([$usuario_id, $producto_id]);
    echo json_encode(['ok' => true, 'accion' => 'agregado']);
}