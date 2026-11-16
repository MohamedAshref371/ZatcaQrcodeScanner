#import "InvoiceZXingDecoder.h"
@import ZXingCpp;

@implementation InvoiceZXingDecoder
+ (NSArray<NSString *> *)readImage:(CGImageRef)image maxSymbols:(NSInteger)maxSymbols
{
    ZXIReaderOptions *options = [ZXIReaderOptions new];
    options.formats = @[@(QR_CODE)];
    options.tryHarder = YES;
    options.tryRotate = YES;
    options.tryInvert = NO;
    options.tryDownscale = YES;
    options.binarizer = ZXIBinarizerLocalAverage;
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
