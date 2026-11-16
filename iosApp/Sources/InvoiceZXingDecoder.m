#import "InvoiceZXingDecoder.h"
@import ZXingCpp;
@import UIKit;

@implementation InvoiceZXingObservation
@end

@implementation InvoiceZXingDecoder
+ (NSArray<InvoiceZXingObservation *> *)readImage:(CGImageRef)image
                       maxSymbols:(NSInteger)maxSymbols
                          harder:(BOOL)harder
                         denoise:(BOOL)denoise
                          rotate:(BOOL)rotate
                        inverted:(BOOL)inverted
                       binarizer:(NSInteger)binarizer
                     returnErrors:(BOOL)returnErrors
{
    ZXIReaderOptions *options = [ZXIReaderOptions new];
    options.formats = @[@(QR_CODE)];
    options.tryHarder = harder;
    options.tryDenoise = denoise;
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
    options.returnErrors = returnErrors;
    ZXIBarcodeReader *reader = [[ZXIBarcodeReader alloc] initWithOptions:options];
    NSMutableArray<InvoiceZXingObservation *> *observations = [NSMutableArray array];
    for (ZXIResult *result in [reader readCGImage:image error:NULL]) {
        InvoiceZXingObservation *observation = [InvoiceZXingObservation new];
        observation.text = result.valid && result.text.length > 0 ? result.text : nil;
        observation.recoverable = result.recoverable;
        if (returnErrors) {
            ZXIPosition *position = result.position;
            observation.corners = @[
                [NSValue valueWithCGPoint:CGPointMake(position.topLeft.x, position.topLeft.y)],
                [NSValue valueWithCGPoint:CGPointMake(position.topRight.x, position.topRight.y)],
                [NSValue valueWithCGPoint:CGPointMake(position.bottomRight.x, position.bottomRight.y)],
                [NSValue valueWithCGPoint:CGPointMake(position.bottomLeft.x, position.bottomLeft.y)],
            ];
        } else {
            observation.corners = @[];
        }
        [observations addObject:observation];
    }
    return observations;
}
@end
