package com.nttdocomo.ui;
final class AndroidMediaImage implements MediaImage {
    private final Image image;
    AndroidMediaImage(Image i){ image=i; }
    public Image getImage(){ return image; }
    public void use(){}
}
