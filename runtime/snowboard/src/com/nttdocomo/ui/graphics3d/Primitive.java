package com.nttdocomo.ui.graphics3d;

/** Minimal DoJa Primitive container with the constructor metadata retained. */
public class Primitive extends Object3D implements DrawableObject3D {
    public final int type;
    public final int attributes;
    public final int primitiveCount;
    public final int verticesPerPrimitive;

    private final int[] vertices;
    private final int[] colors;
    private final int[] tex;

    public Texture texture;
    public int blend;
    public float transparency = 100f;

    public Primitive(int type, int attributes, int primitiveCount) {
        this.type = type;
        this.attributes = attributes;
        this.primitiveCount = Math.max(0, primitiveCount);
        this.verticesPerPrimitive = verticesForType(type);

        int vertexCount = Math.max(1, this.primitiveCount * this.verticesPerPrimitive);
        this.vertices = new int[Math.max(3, vertexCount * 3)];

        // Snowboarding requests color storage only when either 0x0400/0x0800 is set.
        // Give it enough room for per-primitive/per-face data without imposing a format.
        this.colors = ((attributes & 0x0C00) != 0)
                ? new int[Math.max(1, this.primitiveCount * 2)] : null;

        // The supplied game tests 0x3000 before asking for texture-coordinate storage.
        this.tex = ((attributes & 0x3000) != 0)
                ? new int[Math.max(2, vertexCount * 2)] : null;
    }

    private static int verticesForType(int type) {
        switch (type) {
            case 1: return 1; // points
            case 2: return 2; // lines
            case 3: return 3; // triangles
            case 4: return 4; // quads (Snowboarding terrain path)
            case 5: return 1; // point sprites
            default: return Math.max(1, Math.min(4, type));
        }
    }

    public int[] getVertexArray(){ return vertices; }
    public int[] getColorArray(){ return colors; }
    public int[] getTextureCoordArray(){ return tex; }
    public void setBlendMode(int m){ blend=m; }
    public void setTexture(Texture t){ texture=t; }
    public void setTransparency(float f){ transparency=f; }
}
