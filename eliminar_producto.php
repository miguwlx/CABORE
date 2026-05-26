<?php
session_start();
include("conexion.php");

$usuario_id = $_SESSION['usuario_id'];

$nombre = $_POST['nombre'];
$descripcion = $_POST['descripcion'];
$color = $_POST['color'];

/* IMAGEN LOGO */
$logo = "";

if(isset($_FILES['logo']) && $_FILES['logo']['name'] != ""){
    $logo = "img/logos/" . $_FILES['logo']['name'];
    move_uploaded_file($_FILES['logo']['tmp_name'], $logo);

    $sql = "UPDATE tiendas 
            SET nombre='$nombre', descripcion='$descripcion', color='$color', logo='$logo'
            WHERE usuario_id=$usuario_id";
}else{
    $sql = "UPDATE tiendas 
            SET nombre='$nombre', descripcion='$descripcion', color='$color'
            WHERE usuario_id=$usuario_id";
}

$conn->query($sql);

header("Location: emprendedor.php");
?>