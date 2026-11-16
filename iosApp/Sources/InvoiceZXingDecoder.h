#import <CoreGraphics/CoreGraphics.h>
#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface InvoiceZXingDecoder : NSObject
+ (NSArray<NSString *> *)readImage:(CGImageRef)image maxSymbols:(NSInteger)maxSymbols
    NS_SWIFT_NAME(read(image:maxSymbols:));
@end

NS_ASSUME_NONNULL_END
