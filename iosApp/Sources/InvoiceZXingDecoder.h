#import <CoreGraphics/CoreGraphics.h>
#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface InvoiceZXingDecoder : NSObject
+ (NSArray<NSString *> *)readImage:(CGImageRef)image
                       maxSymbols:(NSInteger)maxSymbols
                          harder:(BOOL)harder
                          rotate:(BOOL)rotate
                        inverted:(BOOL)inverted
                       binarizer:(NSInteger)binarizer
    NS_SWIFT_NAME(read(image:maxSymbols:harder:rotate:inverted:binarizer:));
@end

NS_ASSUME_NONNULL_END
