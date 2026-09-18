<?php
session_start();
include("conexion.php");

if (!isset($_SESSION['usuario_id']) || $_SESSION['rol'] !== 'emprendedor') {
    header("Location: login.html"); exit();
}

$usuario_id = (int)$_SESSION['usuario_id'];
$id         = (int)($_POST['id'] ?? 0);

// Solo borrar si el producto pertenece a una tienda del usuario logueado
$stmt = $conn->prepare(
    "DELETE p FROM productos p
     INNER JOIN tiendas t ON p.tienda_id = t.id
     WHERE p.id = ? AND t.usuario_id = ?"
);
$stmt->bind_param("ii", $id, $usuario_id);
$stmt->execute();

header("Location: emprendedor.php");