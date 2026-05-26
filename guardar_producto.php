<?php
include("conexion.php");

$id = $_POST['id'];
$nombre = $_POST['nombre'];
$precio = $_POST['precio'];
$descripcion = $_POST['descripcion'];

$sql = "UPDATE productos 
        SET nombre='$nombre', precio='$precio', descripcion='$descripcion'
        WHERE id=$id";

if ($conn->query($sql)) {
    header("Location: emprendedor.php");
} else {
    echo "Error";
}
?>