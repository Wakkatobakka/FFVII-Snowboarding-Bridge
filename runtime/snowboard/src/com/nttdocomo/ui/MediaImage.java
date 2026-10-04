package com.nttdocomo.ui;
/** DoJa exposes MediaImage as an interface. Keep this binary shape exact because
 * game bytecode uses invokeinterface for getImage(). */
public interface MediaImage extends MediaResource {
    Image getImage();
}
