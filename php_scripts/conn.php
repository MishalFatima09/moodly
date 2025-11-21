<?php
//----------------- CONFIG ------------------//
$DB_HOST     = 'aws-1-ap-southeast-1.pooler.supabase.com';
$DB_PORT     = '5432';
$DB_NAME     = 'postgres';
$DB_USER     = 'postgres.yicntsohozgrbhjwccsd';
$DB_PASSWORD = 'aloo_kabab_123';

// Create PDO connection
try {
    $pdo = new PDO(
        "pgsql:host={$DB_HOST};port={$DB_PORT};dbname={$DB_NAME}",
        $DB_USER,
        $DB_PASSWORD,
        [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC
        ]
    );
} catch (PDOException $e) {
    http_response_code(500);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode([
        'error' => 'DB connection failed',
        'message' => $e->getMessage()
    ]);
    exit;
}
