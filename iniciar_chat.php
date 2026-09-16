<?php
session_start();
include("conexion.php");

if (!isset($_SESSION['usuario_id']) || $_SESSION['rol'] !== 'cliente') {
    header("Location: login.html"); exit();
}

$usuario_id = (int)$_SESSION['usuario_id'];
$tienda_id  = (int)($_GET['tienda_id'] ?? 0);

if ($tienda_id <= 0) { header("Location: cliente.php"); exit(); }

// Verificar que la tienda existe
$stmtT = $conn->prepare("SELECT id FROM tiendas WHERE id = ?");
$stmtT->bind_param("i", $tienda_id);
$stmtT->execute();
if ($stmtT->get_result()->num_rows === 0) { header("Location: cliente.php"); exit(); }

// Buscar conversación existente entre este cliente y esta tienda
$stmt = $conn->prepare("SELECT id FROM conversaciones WHERE tienda_id = ? AND cliente_id = ?");
$stmt->bind_param("ii", $tienda_id, $usuario_id);
$stmt->execute();
$conv = $stmt->get_result()->fetch_assoc();

if ($conv) {
    $conversacion_id = $conv['id'];
} else {
    $ins = $conn->prepare("INSERT INTO conversaciones (tienda_id, cliente_id) VALUES (?, ?)");
    $ins->bind_param("ii", $tienda_id, $usuario_id);
    $ins->execute();
    $conversacion_id = $ins->insert_id;
}

header("Location: chat.php?conversacion_id=$conversacion_id");
exit();