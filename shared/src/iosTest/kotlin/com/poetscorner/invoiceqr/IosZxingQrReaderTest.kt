package com.poetscorner.invoiceqr

import com.poetscorner.invoiceqr.scanner.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosZxingQrReaderTest {
    @Test fun decodesAndroidInvoiceFixtureWithExplicitOptionsAndRotation() {
        val modules = qr.trimIndent().lines()
        val scale = 8
        val edge = modules.size * scale
        val pixels = ByteArray(edge * edge) { index ->
            if (modules[index / edge / scale][index % edge / scale] == '#') 0 else 255.toByte()
        }
        val options = QrReadOptions(tryHarder = true, tryDenoise = true, tryRotate = false,
            tryDownscale = false, binarizer = QrBinarizer.FIXED_THRESHOLD,
            maxNumberOfSymbols = 2, returnErrors = true)
        val expected = IosZxingQrReader.read(LumaImage(pixels, edge, edge), options).single()
        val rotated = IosZxingQrReader.read(LumaImage(pixels, edge, edge, 90), options).single()
        assertNull(rotated.error)
        assertEquals(expected.text, rotated.text)
        assertEquals("AQVTdG9yZQIPMzAwMDAwMDAwMDAwMDAzAxkyMDI2LTAxLTAxVDEyOjAwOjAwKzAzOjAwBAYxMTUuMDAFBTE1LjAw", rotated.text)
    }

    // Module samples from app/src/androidTest/assets/valid-invoice-qr.png.
    private val qr = """
        .........................................
        .........................................
        ..#######.###.###.#.##.....##.#.#######..
        ..#.....#.###..##..###..#.###...#.....#..
        ..#.###.#.#.##..#.##.###.##.#.#.#.###.#..
        ..#.###.#.##.#.##.###.#..#.##...#.###.#..
        ..#.###.#......#.##.#..##....##.#.###.#..
        ..#.....#.#..##.....##....###.#.#.....#..
        ..#######.#.#.#.#.#.#.#.#.#.#.#.#######..
        ...........#..#.##.##..#..#..##..........
        ..##..###....#.###...##.#........#.####..
        ...##.#....##.#.#.##.#.#....#.#.#.##..#..
        ...########..#####.#.....#.#.###.........
        ..##..##...#.#.#.#####.#.#..##...#.###...
        ..##....###.#.##..##..####.....########..
        ...#...#....#..###.#...#.#....##..#.#....
        ..#.###.####.....#.#..#.###..#...#..#....
        ..#..#.#..#.#.#.##.#.#####..##..###.##...
        ..##...###..##.##.....#.#.##.#.########..
        ....#.##..###.#...####......#.#.#.#.###..
        ..##..#.##...########..###..##..###.#....
        ..#..#.#.....#.#.#.###.#.##.#....#..###..
        ..#####.##..#.##..#....##..#..###..#.##..
        ....#....#.#...##.#.....#..####.#.#.###..
        ....###.###......####.#..#..##...#.#.....
        ..........#.#.#.#.##...#.##..###....#....
        .......##.#.##.##....##.####...#####..#..
        ..##.#...####.#...#..#.#....#.#.#.#..##..
        ........####.########....#...###..#.#....
        ....#.##.....#.#...###.#.#..##....#.#....
        ..##.##.#.#.#.##...#...###..#.#####...#..
        ..........###..##.#....#.#.#..#...#.#.#..
        ..#######..##....#.##.#.####.##.#.#.#....
        ..#.....#.#.#.#.######.###....#...#.##...
        ..#.###.#.##.#.##.....#.#.##..######.....
        ..#.###.#..#..#...####......##.##....##..
        ..#.###.#....########.####..#...##.##....
        ..#.....#.####.#.#.#####.##..##..#..##...
        ..#######.#.#.##.....#.##..#.#.#.....##..
        .........................................
        .........................................
    """
}
