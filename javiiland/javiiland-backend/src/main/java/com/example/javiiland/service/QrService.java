package com.example.javiiland.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Service
public class QrService {
    public byte[] generar(String contenido) {
        try {
            var matriz = new QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, 300, 300);
            var out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matriz, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el QR", e);
        }
    }
}