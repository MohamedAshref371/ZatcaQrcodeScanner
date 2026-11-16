#import "InvoiceZXingDecoder.h"
@import ZXingCpp;

@implementation InvoiceZXingDecoder
+ (NSArray<NSString *> *)readImage:(CGImageRef)image
                       maxSymbols:(NSInteger)maxSymbols
                          harder:(BOOL)harder
                          rotate:(BOOL)rotate
                        inverted:(BOOL)inverted
                       binarizer:(NSInteger)binarizer
{
    ZXIReaderOptions *options = [ZXIReaderOptions new];
    options.formats = @[@(QR_CODE)];
    options.tryHarder = harder;
    options.tryRotate = rotate;
    options.tryInvert = inverted;
    options.tryDownscale = YES;
    switch (binarizer) {
        case 1: options.binarizer = ZXIBinarizerGlobalHistogram; break;
        case 2: options.binarizer = ZXIBinarizerFixedThreshold; break;
        case 3: options.binarizer = ZXIBinarizerBoolCast; break;
        default: options.binarizer = ZXIBinarizerLocalAverage; break;
    }
    options.textMode = ZXITextModePlain;
    options.maxNumberOfSymbols = maxSymbols;
    options.returnErrors = NO;
    ZXIBarcodeReader *reader = [[ZXIBarcodeReader alloc] initWithOptions:options];
    NSMutableArray<NSString *> *codes = [NSMutableArray array];
    for (ZXIResult *result in [reader readCGImage:image error:NULL]) {
        if (result.text.length > 0) {
            [codes addObject:result.text];
        }
    }
    return codes;
}
@end
