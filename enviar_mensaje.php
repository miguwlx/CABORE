<?php
session_start();
include("conexion.php");

if (!isset($_SESSION['usuario_id'])) { header("Location: login.html"); exit(); }

$usuario_id       = (int)$_SESSION['usuario_id'];
$rol              = $_SESSION['rol'] ?? '';
$conversacion_id  = (int)($_POST['conversacion_id'] ?? 0);
$contenido        = trim($_POST['contenido'] ?? '');

if ($conversacion_id <= 0 || $contenido === '') {
    header("Location: chat.php?conversacion_id=$conversacion_id");
    exit();
}

// Verificar que el usuario realmente pertenece a esta conversación
if ($rol === 'cliente') {
    $stmt = $conn->prepare("SELECT id FROM conversaciones WHERE id = ? AND cliente_id = ?");
    $stmt->bind_param("ii", $conversacion_id, $usuario_id);
} elseif ($rol === 'emprendedor') {
    $stmt = $conn->prepare("
        SELECT c.id FROM conversaciones c
        JOIN tiendas t ON t.id = c.tienda_id
        WHERE c.id = ? AND t.usuario_id = ?
    ");
    $stmt->bind_param("ii", $conversacion_id, $usuario_id);
} else {
    header("Location: login.html"); exit();
}
$stmt->execute();

if ($stmt->get_result()->num_rows === 0) {
    header("Location: chat.php"); exit();
}

// Insertar mensaje
$ins = $conn->prepare("INSERT INTO mensajes (conversacion_id, emisor_id, contenido) VALUES (?, ?, ?)");
$ins->bind_param("iis", $conversacion_id, $usuario_id, $contenido);
$ins->execute();

// Actualizar fecha de la conversación (para que suba al tope de la lista)
$upd = $conn->prepare("UPDATE conversaciones SET actualizado_en = NOW() WHERE id = ?");
$upd->bind_param("i", $conversacion_id);
$upd->execute();

header("Location: chat.php?conversacion_id=$conversacion_id");
exit();