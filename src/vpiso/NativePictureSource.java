package vpiso;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Picture copies need visible color, irrespective of a framebuffer's auxiliary read selection. */
public final class NativePictureSource {
    public static long begin(int source) {
        if (!Main.compatible || source == 0) return -1L;
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source);
        int buffer;
        try {
            buffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        } finally { GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read); }
        return ((long) source << 32) | (buffer & 0xffffffffL);
    }

    public static void end(long saved) {
        if (saved == -1L) return;
        // Preserve the copy's own final FBO bindings, including its error path.
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, (int) (saved >>> 32));
        try { GL11.glReadBuffer((int) saved); }
        finally { GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read); }
    }

    private NativePictureSource() {}
}
