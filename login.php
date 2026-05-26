<?php
session_start();
include("conexion.php");

// Usar el usuario de la sesión (no hardcodeado)
$usuario_id = $_SESSION['usuario_id'];

$nombre = $_POST['nombre'];
$descripcion = $_POST['descripcion'];

$sql = "INSERT INTO tiendas (usuario_id, nombre, descripcion)
        VALUES ('$usuario_id', '$nombre', '$descripcion')";

if ($conn->query($sql)) {
    header("Location: emprendedor.php");
} else {
    echo "Error al guardar la tienda";
}
?>
