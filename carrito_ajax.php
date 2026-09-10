<?php
/**
 * carrito_ajax.php — Carrito de compras (100% servidor, aislado por usuario)
 *
 * IMPORTANTE (corrección de bug): antes el carrito real vivía en localStorage
 * del navegador ('cabore_cart'), que es un almacenamiento compartido por
 * NAVEGADOR, no por usuario. Si dos personas usaban el mismo computador/
 * navegador (o cambiaban de cuenta), veían y mezclaban el carrito de otra
 * cuenta. Ahora el carrito vive únicamente en $_SESSION, atado al usuario_id
 * autenticado, y se vuelve a inicializar automáticamente si detecta que la
 * sesión pertenece a un usuario distinto (protección extra ante cualquier
 * reutilización de sesión).
 */
session_start();
require_once 'conexion.php';

header('Content-Type: application/json');

// ── Debe haber sesión iniciada ──────────────────────────────
if (!isset($_SESSION['usuario_id'])) {
    http_response_code(401);
    echo json_encode(['ok' => false, 'error' => 'Debes iniciar sesión para usar el carrito.']);
    exit();
}

$usuario_id = (int) $_SESSION['usuario_id'];

// ── Carrito SIEMPRE atado al usuario actual ─────────────────
// Si el carrito guardado en sesión no pertenece a este usuario_id, se reinicia.
// Esto es lo que garantiza que el carrito nunca se mezcle entre cuentas.
if (!isset($_SESSION['carrito']) || !isset($_SESSION['carrito_usuario_id']) || $_SESSION['carrito_usuario_id'] !== $usuario_id) {
    $_SESSION['carrito']            = [];
    $_SESSION['carrito_usuario_id'] = $usuario_id;
}

function carrito_respuesta($pdo, $usuario_id) {
    $items  = [];
    $total  = 0;
    $count  = 0;
    $cambio = false;

    foreach ($_SESSION['carrito'] as $prod_id => $cantidad) {
        $stmt = $pdo->prepare("
            SELECT p.id, p.nombre, p.precio, p.precio_oferta, p.imagen, p.stock, p.activo,
                   t.nombre AS tienda
            FROM productos p
            JOIN tiendas t ON t.id = p.tienda_id
            WHERE p.id = ?
        ");
        $stmt->execute([$prod_id]);
        $p = $stmt->fetch();

        // El producto pudo eliminarse, desactivarse o quedarse sin stock desde que se agregó
        if (!$p || !$p['activo'] || $p['stock'] <= 0) {
            unset($_SESSION['carrito'][$prod_id]);
            $cambio = true;
            continue;
        }

        // Ajustar cantidad si supera el stock disponible
        if ($cantidad > $p['stock']) {
            $cantidad = $p['stock'];
            $_SESSION['carrito'][$prod_id] = $cantidad;
            $cambio = true;
        }

        $precio = ($p['precio_oferta'] && $p['precio_oferta'] < $p['precio']) ? $p['precio_oferta'] : $p['precio'];

        $items[] = [
            'id'       => (int) $p['id'],
            'nombre'   => $p['nombre'],
            'precio'   => (float) $precio,
            'imagen'   => $p['imagen'],
            'tienda'   => $p['tienda'],
            'cantidad' => (int) $cantidad,
            'stock'    => (int) $p['stock'],
        ];

        $total += $precio * $cantidad;
        $count += $cantidad;
    }

    return [
        'ok'     => true,
        'items'  => $items,
        'total'  => $total,
        'count'  => $count,
        'cambio' => $cambio, // true si algo se ajustó/eliminó automáticamente
    ];
}

$accion = $_POST['accion'] ?? '';

switch ($accion) {

    case 'listar':
        echo json_encode(carrito_respuesta($pdo, $usuario_id));
        break;

    case 'agregar':
        $id = (int) ($_POST['id'] ?? 0);
        if ($id <= 0) {
            echo json_encode(['ok' => false, 'error' => 'Producto inválido.']);
            break;
        }

        $stmt = $pdo->prepare("SELECT id, stock, activo FROM productos WHERE id = ?");
        $stmt->execute([$id]);
        $prod = $stmt->fetch();

        if (!$prod || !$prod['activo']) {
            echo json_encode(['ok' => false, 'error' => 'El producto no está disponible.']);
            break;
        }
        if ($prod['stock'] <= 0) {
            echo json_encode(['ok' => false, 'error' => 'Sin stock disponible.']);
            break;
        }

        $actual = $_SESSION['carrito'][$id] ?? 0;
        if ($actual + 1 > $prod['stock']) {
            echo json_encode(['ok' => false, 'error' => 'No hay más stock disponible de este producto.']);
            break;
        }

        $_SESSION['carrito'][$id] = $actual + 1;
        echo json_encode(carrito_respuesta($pdo, $usuario_id));
        break;

    case 'incrementar':
        $id = (int) ($_POST['id'] ?? 0);
        if (isset($_SESSION['carrito'][$id])) {
            $stmt = $pdo->prepare("SELECT stock FROM productos WHERE id = ?");
            $stmt->execute([$id]);
            $stock = (int) ($stmt->fetchColumn() ?: 0);
            if ($_SESSION['carrito'][$id] + 1 <= $stock) {
                $_SESSION['carrito'][$id]++;
            }
        }
        echo json_encode(carrito_respuesta($pdo, $usuario_id));
        break;

    case 'disminuir':
        $id = (int) ($_POST['id'] ?? 0);
        if (isset($_SESSION['carrito'][$id])) {
            $_SESSION['carrito'][$id]--;
            if ($_SESSION['carrito'][$id] <= 0) {
                unset($_SESSION['carrito'][$id]);
            }
        }
        echo json_encode(carrito_respuesta($pdo, $usuario_id));
        break;

    case 'eliminar':
        $id = (int) ($_POST['id'] ?? 0);
        unset($_SESSION['carrito'][$id]);
        echo json_encode(carrito_respuesta($pdo, $usuario_id));
        break;

    case 'vaciar':
        $_SESSION['carrito'] = [];
        echo json_encode(carrito_respuesta($pdo, $usuario_id));
        break;

    default:
        http_response_code(400);
        echo json_encode(['ok' => false, 'error' => 'Acción no reconocida.']);
}