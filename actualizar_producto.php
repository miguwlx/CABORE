<?php
session_start();
include("conexion.php");

if (!isset($_SESSION['usuario_id']) || $_SESSION['rol'] !== 'emprendedor') {
    header("Location: login.html"); exit();
}

$id          = (int)$_POST['id'];
$nombre      = trim($_POST['nombre'] ?? '');
$precio      = (float)($_POST['precio'] ?? 0);
$descripcion = trim($_POST['descripcion'] ?? '');

$stmt = $conn->prepare(
    "UPDATE productos SET nombre=?, precio=?, descripcion=? WHERE id=?"
);
$stmt->bind_param("sdsi", $nombre, $precio, $descripcion, $id);
$stmt->execute();

header("Location: emprendedor.php");