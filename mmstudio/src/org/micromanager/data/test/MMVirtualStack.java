package org.micromanager.data.test;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.ByteProcessor;

import org.micromanager.api.data.Coords;
import org.micromanager.api.data.Datastore;

import org.micromanager.data.DefaultCoords;
import org.micromanager.data.DefaultImage;

import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.ReportingUtils;

/**
 * This stack class provides the ImagePlus with images from the Datastore.
 * It is needed to let ImageJ access our data, so that ImageJ tools and plugins
 * can operate on it. Though, since ImageJ only knows about three axes
 * (channel, frame, and slice), it won't be able to access the *entire*
 * dataset if there are other axes (like, say, stage position). It can only
 * ever access an XYZTC volume.
 */
public class MMVirtualStack extends ij.VirtualStack {
   private Datastore store_;
   private ImagePlus plus_;
   private Coords curCoords_;

   public MMVirtualStack(Datastore store) {
      store_ = store;
      curCoords_ = new DefaultCoords.Builder().build();
   }

   public void setImagePlus(ImagePlus plus) {
      plus_ = plus;
   }

   /**
    * Retrieve the pixel buffer for the image at the specified offset. See
    * getImage() for more details.
    */
   @Override
   public Object getPixels(int flatIndex) {
      if (plus_ == null) {
         ReportingUtils.logError("Asked to get pixels when I don't have an ImagePlus");
         return null;
      }
      DefaultImage image = getImage(flatIndex);
      ReportingUtils.logError("Asked for image at " + flatIndex + " a.k.a. " + image.getCoords() + " considering our position at " + curCoords_);
      if (image != null) {
         return image.getRawPixels();
      }
      ReportingUtils.logError("Null image at " + curCoords_);
      return null;
   }

   /**
    * Retrieve the image at the specified index, which we map into a
    * channel/frame/z offset. This will also update curCoords_ as needed.
    * Note that we only pay attention to a given offset if we already have
    * a position along that axis (e.g. so that we don't try to ask the
    * Datastore for an image at time=0 when none of the images in the Datastore
    * have a time axis whatsoever).
    */
   private DefaultImage getImage(int flatIndex) {
      // Note: index is 1-based.
      if (flatIndex > plus_.getStackSize()) {
         ReportingUtils.logError("Stack asked for image at " + flatIndex + 
               " that exceeds total of " + plus_.getStackSize() + " images");
         return null;
      }
      // These coordinates are missing all axes that ImageJ doesn't know 
      // about (e.g. stage position), so we augment curCoords with them and
      // use that for our lookup.
      // If we have no ImagePlus yet to translate this index into something
      // more meaningful, then default to all zeros.
      int channel = 0, z = 0, frame = 0;
      if (plus_ != null) {
         int[] pos3D = plus_.convertIndexToPosition(flatIndex);
         // ImageJ coordinates are 1-indexed.
         channel = pos3D[0] - 1;
         z = pos3D[1] - 1;
         frame = pos3D[2] - 1;
      }
      // Only augment a given axis if it's actually present in our datastore.
      Coords.CoordsBuilder builder = curCoords_.copy();
      if (store_.getMaxIndex("channel") != -1) {
         builder.position("channel", channel);
      }
      if (store_.getMaxIndex("z") != -1) {
         builder.position("z", z);
      }
      if (store_.getMaxIndex("frame") != -1) {
         builder.position("frame", frame);
      }
      curCoords_ = builder.build();
      return (DefaultImage) store_.getImage(curCoords_);
   }

   @Override
   public ImageProcessor getProcessor(int flatIndex) {
      if (plus_ == null) {
         // We have to supply a processor, even if it's totally bogus.
         return new ByteProcessor(1, 1);
      }
      DefaultImage image = getImage(flatIndex);
      int width = image.getWidth();
      int height = image.getHeight();
      int depth = image.getMetadata().getBitDepth();
      // TODO: find a better way of setting the mode.
      int mode = ImagePlus.GRAY8;
      if (depth > 8) {
         mode = ImagePlus.GRAY16;
      }
      Object pixels = image.getRawPixels();
      ImageProcessor result = ImageUtils.makeProcessor(mode, width, height, pixels);
      return result;
   }

   @Override
   public int getSize() {
      return 1;
   }

   @Override
   public String getSliceLabel(int n) {
      return Integer.toString(n);
   }

   /**
    * Update the current position we are centered on. This in turn will affect
    * future calls to getPixels().
    */
   public void setCoords(Coords coords) {
      curCoords_ = coords;
      plus_.setPosition(coords.getPositionAt("channel") + 1,
            coords.getPositionAt("z") + 1, coords.getPositionAt("frame") + 1);
   }

   /**
    * Return our current coordinates.
    */
   public Coords getCurrentImageCoords() {
      return curCoords_;
   }
}