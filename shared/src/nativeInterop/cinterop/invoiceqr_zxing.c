#include "invoiceqr_zxing.h"
#include <stddef.h>
#include "ZXingC.h"

InvoiceQrSymbols* invoiceqr_read(const uint8_t* pixels, int size, int width, int height, int rotation,
    bool harder, bool denoise, bool rotate, bool invert, bool downscale,
    int binarizer, int max_symbols, bool return_errors)
{
    static const ZXing_Binarizer binarizers[] = {
        ZXing_Binarizer_LocalAverage, ZXing_Binarizer_GlobalHistogram,
        ZXing_Binarizer_FixedThreshold, ZXing_Binarizer_BoolCast
    };
    if (binarizer < 0 || binarizer >= (int)(sizeof(binarizers) / sizeof(binarizers[0])))
        return NULL;
    ZXing_ImageView* image = ZXing_ImageView_new_checked(pixels, size, width, height,
        ZXing_ImageFormat_Lum, width, 1);
    if (!image)
        return NULL;
    ZXing_ReaderOptions* options = ZXing_ReaderOptions_new();
    if (!options) {
        ZXing_ImageView_delete(image);
        return NULL;
    }
    const ZXing_BarcodeFormat qr = ZXing_BarcodeFormat_QRCode;
    ZXing_ImageView_rotate(image, rotation);
    ZXing_ReaderOptions_setFormats(options, &qr, 1);
    ZXing_ReaderOptions_setTextMode(options, ZXing_TextMode_Plain);
    ZXing_ReaderOptions_setTryHarder(options, harder);
    ZXing_ReaderOptions_setTryDenoise(options, denoise);
    ZXing_ReaderOptions_setTryRotate(options, rotate);
    ZXing_ReaderOptions_setTryInvert(options, invert);
    ZXing_ReaderOptions_setTryDownscale(options, downscale);
    ZXing_ReaderOptions_setBinarizer(options, binarizers[binarizer]);
    ZXing_ReaderOptions_setMaxNumberOfSymbols(options, max_symbols);
    ZXing_ReaderOptions_setReturnErrors(options, return_errors);
    ZXing_Barcodes* results = ZXing_ReadBarcodes(image, options);
    ZXing_ReaderOptions_delete(options);
    ZXing_ImageView_delete(image);
    return (InvoiceQrSymbols*)results;
}

void invoiceqr_symbols_delete(InvoiceQrSymbols* symbols)
{
    ZXing_Barcodes_delete((ZXing_Barcodes*)symbols);
}

int invoiceqr_symbols_count(const InvoiceQrSymbols* symbols)
{
    return ZXing_Barcodes_size((const ZXing_Barcodes*)symbols);
}

char* invoiceqr_symbol_read(const InvoiceQrSymbols* symbols, int index, int corners[8], int* format, int* error)
{
    const ZXing_Barcode* barcode = ZXing_Barcodes_at((const ZXing_Barcodes*)symbols, index);
    ZXing_Position p = ZXing_Barcode_position(barcode);
    corners[0] = p.topLeft.x; corners[1] = p.topLeft.y;
    corners[2] = p.topRight.x; corners[3] = p.topRight.y;
    corners[4] = p.bottomRight.x; corners[5] = p.bottomRight.y;
    corners[6] = p.bottomLeft.x; corners[7] = p.bottomLeft.y;
    switch (ZXing_Barcode_format(barcode)) {
        case ZXing_BarcodeFormat_QRCode: *format = 1; break;
        case ZXing_BarcodeFormat_QRCodeModel1: *format = 2; break;
        case ZXing_BarcodeFormat_QRCodeModel2: *format = 3; break;
        default: *format = 0;
    }
    switch (ZXing_Barcode_errorType(barcode)) {
        case ZXing_ErrorType_None: *error = 0; break;
        case ZXing_ErrorType_Format: *error = 1; break;
        case ZXing_ErrorType_Checksum: *error = 2; break;
        default: *error = 3;
    }
    return ZXing_Barcode_text(barcode);
}

void invoiceqr_text_delete(char* text)
{
    ZXing_free(text);
}
