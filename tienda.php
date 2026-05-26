<?php
$conn = new mysqli("localhost", "root", "", "cabore");

if ($conn->connect_error) {
    die("Error de conexión");
}
?>