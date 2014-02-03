import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Paths;

public class TestNxtSolver {

    private static NxtSolver solver;

    @BeforeClass
    public static void init() {
        solver = new NxtSolver(Paths.get("."));
    }

    @Test
    public void solver() {
        Assert.assertEquals("tts", solver.solve("*ts", ""));
        Assert.assertEquals("tts", solver.solve("t*s", ""));
        Assert.assertEquals("tts", solver.solve("tt*", ""));
        Assert.assertEquals("tts", solver.solve("***", "_{r"));
    }

    @Test
    public void wildcardPrefix() {
        Assert.assertEquals("tts", solver.solve("***ABC", ""));
    }

    @Test
    public void offsets() {
        Assert.assertArrayEquals(new int[]{16, 16, 16}, solver.getOffsets("***", "000"));
        Assert.assertArrayEquals(new int[]{-1, 16, -1, 16, -1, 16}, solver.getOffsets("X*Y*Z*", "000"));
        Assert.assertArrayEquals(new int[]{-1, 16, -1, 16, -1, 65}, solver.getOffsets("X*Y*Z*", "00a"));
        Assert.assertArrayEquals(new int[]{-1, -1, -1}, solver.getOffsets("XYZ", "00a"));
        Assert.assertArrayEquals(new int[]{-1, 65, 65, 0, -1, -1}, solver.getOffsets("X***YZ", "aa"));
    }

    @Test
    public void counter() {
        Assert.assertEquals(0, solver.getCounter(new int[]{}));
        Assert.assertEquals(0, solver.getCounter(new int[]{0, 0, 0}));
        Assert.assertEquals(0, solver.getCounter(new int[]{-1, -1, -1}));
        int radix = solver.chars.length();
        Assert.assertEquals(radix, solver.getCounter(new int[]{0, 1, -1}));
        Assert.assertEquals(radix*radix + 2*radix + 3, solver.getCounter(new int[]{3, 2, 1}));
    }

    @Test
    public void secretPhrase() {
        StringBuilder sb = new StringBuilder();
        int[] offsets1 = { 0,0,0 };
        Assert.assertEquals("   ", solver.getSecretPhrase("***", solver.getCounter(offsets1), offsets1, sb));
        int[] offsets2 = { 60,10,1 };
        int counter = solver.getCounter(offsets2);
        Assert.assertEquals("\\*!", solver.getSecretPhrase("***", counter, offsets2, sb));
        counter ++;
        Assert.assertEquals("]*!", solver.getSecretPhrase("***", counter, offsets2, sb));
        counter ++;
        Assert.assertEquals("^*!", solver.getSecretPhrase("***", counter, offsets2, sb));
        int[] offsets3 = {-1, 60,-1, 10, -1, 1, -1};
        Assert.assertEquals("X^Y*Z!A", solver.getSecretPhrase("X*Y*Z*A", counter, offsets3, sb));
    }

}
