<?php
include("conexion.php");

$id = $_POST['id'];

$conn->query("DELETE FROM productos WHERE id = $id");

header("Location: emprendedor.php");
?>