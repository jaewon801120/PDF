package de.redsix.pdfcompare;

import static de.redsix.pdfcompare.PdfComparator.MARKER_WIDTH;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.redsix.pdfcompare.env.Environment;

public class DiffImage_SHLife {

    private static final Logger LOG = LoggerFactory.getLogger(DiffImage_SHLife.class);
    /*package*/ static final int MARKER_RGB = color(255, 0, 0);
    private final ImageWithDimension expectedImage;
    private final ImageWithDimension actualImage;
    private final int page;
    private final Environment environment;
    private final Exclusions exclusions;
    private DataBuffer expectedBuffer;
    private DataBuffer actualBuffer;
    private int expectedImageWidth;
    private int expectedImageHeight;
    private int actualImageWidth;
    private int actualImageHeight;
    //private BufferedImage resultImage;
    private BufferedImage expectedResultImage;
    private BufferedImage actualResultImage;
    private int diffAreaX1, diffAreaY1, diffAreaX2, diffAreaY2;
    private final ResultCollector compareResult;
    private PageDiffCalculator diffCalculator;

    public DiffImage_SHLife(final ImageWithDimension expectedImage, final ImageWithDimension actualImage, final int page,
            final Environment environment, final Exclusions exclusions, final ResultCollector compareResult) {
        this.expectedImage = expectedImage;
        this.actualImage = actualImage;
        this.page = page;
        this.environment = environment;
        this.exclusions = exclusions;
        this.compareResult = compareResult;
    }

//    public BufferedImage getImage() {
//        return resultImage;
//    }

    public int getPage() {
        return page;
    }

    public void diffImages() {
        BufferedImage expectBuffImage = this.expectedImage.bufferedImage;
        BufferedImage actualBuffImage = this.actualImage.bufferedImage;
        expectedBuffer = expectBuffImage.getRaster().getDataBuffer();
        actualBuffer = actualBuffImage.getRaster().getDataBuffer();

        expectedImageWidth = expectBuffImage.getWidth();
        expectedImageHeight = expectBuffImage.getHeight();
        actualImageWidth = actualBuffImage.getWidth();
        actualImageHeight = actualBuffImage.getHeight();

        int resultImageWidth = Math.max(expectedImageWidth, actualImageWidth);
        int resultImageHeight = Math.max(expectedImageHeight, actualImageHeight);
        //resultImage = new BufferedImage(resultImageWidth, resultImageHeight, actualBuffImage.getType());
        expectedResultImage = new BufferedImage(resultImageWidth, resultImageHeight, expectBuffImage.getType());
        actualResultImage = new BufferedImage(resultImageWidth, resultImageHeight, actualBuffImage.getType());
        //DataBuffer resultBuffer = resultImage.getRaster().getDataBuffer();
        DataBuffer expectedResultBuffer = expectedResultImage.getRaster().getDataBuffer();
        DataBuffer actualResultBuffer = actualResultImage.getRaster().getDataBuffer();

        diffCalculator = new PageDiffCalculator(resultImageWidth * resultImageHeight, environment.getAllowedDiffInPercent());

        int expectedElement;
        int actualElement;
        final PageExclusions pageExclusions = exclusions.forPage(page + 1);

        for (int y = 0; y < resultImageHeight; y++) {
            final int expectedLineOffset = y * expectedImageWidth;
            final int actualLineOffset = y * actualImageWidth;
            final int resultLineOffset = y * resultImageWidth;
            int nStart = -1;
            int nLast = -1;
            for (int x = 0; x < resultImageWidth; x++) {
                expectedElement = getExpectedElement(x, y, expectedLineOffset);
                actualElement = getActualElement(x, y, actualLineOffset);
                
                //int element = getElement(expectedElement, actualElement);
                int element = -1;
                if (pageExclusions.contains(x, y)) {
                	element = getElement(expectedElement, actualElement);
                    element = ImageTools.fadeExclusion(element);
                    if (expectedElement != actualElement) {
                        diffCalculator.diffFoundInExclusion();
                    }
                    
                    expectedResultBuffer.setElem(x + resultLineOffset, element);
                    actualResultBuffer.setElem(x + resultLineOffset, element);
                } else {
                    if (expectedElement != actualElement) {
                        extendDiffArea(x, y);
                        diffCalculator.diffFound();
                        //LOG.trace("Difference found on page: {} at x: {}, y: {}", page + 1, x, y);
                        //mark(resultBuffer, x, y, resultImageWidth);
                        mark(expectedResultBuffer, x, y, resultImageWidth);
                        mark(actualResultBuffer, x, y, resultImageWidth);
                        
                        if (nStart == -1) {
                        	nStart = x;
                        }
                        
                        if (nLast - nStart > 50) {
                        	nStart = x;
                        }
                        
                        int expectedIntensity = calcCombinedIntensity(expectedElement);
                        int actualIntensity = calcCombinedIntensity(actualElement);
                        if (expectedIntensity > actualIntensity) {
//                        	element = ImageTools.fadeElement(expectedElement);
//                        	
//                        	expectedResultBuffer.setElem(x + resultLineOffset, element);
//                        	actualResultBuffer.setElem(x + resultLineOffset, element);
                            Color color = environment.getActualColor();
                            element = color(levelIntensity(expectedIntensity, color.getRed()), color.getGreen(), color.getBlue());
                            
                            expectedResultBuffer.setElem(x + resultLineOffset, -1);
                            actualResultBuffer.setElem(x + resultLineOffset, element);
                        } else {
                            Color color = environment.getActualColor();
                            element = color(color.getRed(), levelIntensity(actualIntensity, color.getGreen()), color.getBlue());

                            expectedResultBuffer.setElem(x + resultLineOffset, element);
                            actualResultBuffer.setElem(x + resultLineOffset, -1);
                        }
                    }
                    else {
                    	element = ImageTools.fadeElement(expectedElement);
                    	//element = new Color(200, 200, 200).getRGB();
                    	
                    	expectedResultBuffer.setElem(x + resultLineOffset, element);
                    	actualResultBuffer.setElem(x + resultLineOffset, element);
                    }
                }
            }
        }
        if (diffCalculator.differencesFound()) {
            diffCalculator.addDiffArea(new PageArea(page + 1, diffAreaX1, diffAreaY1, diffAreaX2, diffAreaY2));
            LOG.info("Differences found at { page: {}, x1: {}, y1: {}, x2: {}, y2: {} }", page + 1, diffAreaX1, diffAreaY1, diffAreaX2,
                    diffAreaY2);
        }
        final float maxWidth = Math.max(expectedImage.width, actualImage.width);
        final float maxHeight = Math.max(expectedImage.height, actualImage.height);
        //compareResult.addPage(diffCalculator, page, expectedImage, actualImage, new ImageWithDimension(resultImage, maxWidth, maxHeight));
        compareResult.addPage(diffCalculator, page * 2, expectedImage, actualImage, new ImageWithDimension(expectedResultImage, maxWidth, maxHeight));
        compareResult.addPage(diffCalculator, (page * 2) + 1, expectedImage, actualImage, new ImageWithDimension(actualResultImage, maxWidth, maxHeight));
    }

    // 변경된 부분이 있다면 페이지 내 범위를 좌표로 구하기 위해 x, y 최대, 최소 계산
    private void extendDiffArea(final int x, final int y) {
        if (!diffCalculator.differencesFound()) {
            diffAreaX1 = x;
            diffAreaY1 = y;
        }
        diffAreaX1 = Math.min(diffAreaX1, x);
        diffAreaX2 = Math.max(diffAreaX2, x);
        diffAreaY1 = Math.min(diffAreaY1, y);
        diffAreaY2 = Math.max(diffAreaY2, y);
    }

    private int getElement(final int expectedElement, final int actualElement) {
        if (expectedElement != actualElement) {
            int expectedIntensity = calcCombinedIntensity(expectedElement);
            int actualIntensity = calcCombinedIntensity(actualElement);
            if (expectedIntensity > actualIntensity) {
                Color color = environment.getActualColor();
                return color(levelIntensity(expectedIntensity, color.getRed()), color.getGreen(), color.getBlue());
            } else {
                Color color = environment.getExpectedColor();
                return color(color.getRed(), levelIntensity(actualIntensity, color.getGreen()), color.getBlue());
            }
        } else {
            return ImageTools.fadeElement(expectedElement);
        }
    }

    private int getExpectedElement(final int x, final int y, final int expectedLineOffset) {
        if (x < expectedImageWidth && y < expectedImageHeight) {
            return expectedBuffer.getElem(x + expectedLineOffset);
        }
        return 0;
    }

    private int getActualElement(final int x, final int y, final int actualLineOffset) {
        if (x < actualImageWidth && y < actualImageHeight) {
            return actualBuffer.getElem(x + actualLineOffset);
        }
        return 0;
    }

    /**
     * Levels the color intensity to at least 50 and at most maxIntensity.
     *
     * @param darkness     color component to level
     * @param maxIntensity highest possible intensity cut off
     * @return A value that is at least 50 and at most maxIntensity
     */
    private static int levelIntensity(final int darkness, final int maxIntensity) {
        return Math.min(maxIntensity, Math.max(50, darkness));
    }

    /**
     * Calculate the combined intensity of a pixel and normalize it to a value of at most 255.
     *
     * @param element a pixel encoded as an integer
     * @return the intensity of all colors combined cut off at a maximum of 255
     */
    private static int calcCombinedIntensity(final int element) {
        final Color color = new Color(element);
        return Math.min(255, (color.getRed() + color.getGreen() + color.getRed()) / 3);
    }

    // 페이지 상단, 좌측에 변경 부분 표시
    private static void mark(final DataBuffer image, final int x, final int y, final int imageWidth) {
        final int yOffset = y * imageWidth;
        for (int i = 0; i < MARKER_WIDTH; i++) {
            image.setElem(x + i * imageWidth, MARKER_RGB);
            image.setElem(i + yOffset, MARKER_RGB);
        }
    }
    
    public static int color(final int r, final int g, final int b) {
        return new Color(r, g, b).getRGB();
    }

    @Override
    public String toString() {
        return "DiffImage{" +
                "page=" + page +
                '}';
    }
}
