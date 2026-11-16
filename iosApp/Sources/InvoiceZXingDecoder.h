#import <CoreGraphics/CoreGraphics.h>
#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface InvoiceZXingObservation : NSObject
@property(nonatomic, copy, nullable) NSString *text;
@property(nonatomic, copy) NSArray<NSValue *> *corners;
@property(nonatomic) BOOL recoverable;
@end

@interface InvoiceZXingDecoder : NSObject
+ (NSArray<InvoiceZXingObservation *> *)readImage:(CGImageRef)image
                       maxSymbols:(NSInteger)maxSymbols
                          harder:(BOOL)harder
                         denoise:(BOOL)denoise
                          rotate:(BOOL)rotate
                        inverted:(BOOL)inverted
                       binarizer:(NSInteger)binarizer
                     returnErrors:(BOOL)returnErrors
    NS_SWIFT_NAME(read(image:maxSymbols:harder:denoise:rotate:inverted:binarizer:returnErrors:));
@end

NS_ASSUME_NONNULL_END
