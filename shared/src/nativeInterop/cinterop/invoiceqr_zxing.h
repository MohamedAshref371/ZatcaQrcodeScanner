#ifndef INVOICEQR_ZXING_H
#define INVOICEQR_ZXING_H
#include <stdbool.h>
#include <stdint.h>

typedef struct InvoiceQrSymbols InvoiceQrSymbols;

InvoiceQrSymbols* invoiceqr_read(const uint8_t* pixels, int size, int width, int height, int rotation,
    bool harder, bool denoise, bool rotate, bool invert, bool downscale,
    int binarizer, int max_symbols, bool return_errors);
void invoiceqr_symbols_delete(InvoiceQrSymbols* symbols);
int invoiceqr_symbols_count(const InvoiceQrSymbols* symbols);
char* invoiceqr_symbol_read(const InvoiceQrSymbols* symbols, int index, int corners[8], int* format, int* error);
void invoiceqr_text_delete(char* text);
#endif
