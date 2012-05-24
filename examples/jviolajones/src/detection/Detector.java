package detection;

/**
This project is based on the open source jviolajones project created by Simon
Houllier and is used with his permission. Simon's jviolajones project offers 
a pure Java implementation of the Viola-Jones algorithm.

http://en.wikipedia.org/wiki/Viola%E2%80%93Jones_object_detection_framework

The original Java source code for jviolajones can be found here
http://code.google.com/p/jviolajones/ and is subject to the
gnu lesser public license  http://www.gnu.org/licenses/lgpl.html

Many thanks to Simon for his excellent project and for permission to use it 
as the basis of an Aparapi example.
**/

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

public class Detector{

   public static class Stage{
      final int id;

      final List<Tree> trees = new ArrayList<Tree>();

      final float threshold;

      public Stage(int _id, float _threshold) {
         id = _id;
         threshold = _threshold;
      }

      public void addTree(Tree t) {
         trees.add(t);
      }
   }

   public static class Tree{
      final int id;

      final Stage stage;

      final List<Feature> features = new ArrayList<Feature>();

      public Tree(int _id, Stage _stage) {
         id = _id;
         stage = _stage;
      }

      public void addFeature(Feature f) {
         features.add(f);
      }
   }

   public static class Feature{

      final int id;

      final List<Rect> rects = new ArrayList<Rect>();

      final int nb_rects;

      final float threshold;

      final float left_val;

      final float right_val;

      final Point size;

      final int left_node;

      final int right_node;

      final boolean has_left_val;

      final boolean has_right_val;

      final Tree tree;

      public Feature(int _id, Tree _tree, float _threshold, float _left_val, int _left_node, boolean _has_left_val,
            float _right_val, int _right_node, boolean _has_right_val, Point _size) {
         id = _id;
         tree = _tree;
         nb_rects = 0;

         threshold = _threshold;
         left_val = _left_val;
         left_node = _left_node;
         has_left_val = _has_left_val;
         right_val = _right_val;
         right_node = _right_node;
         has_right_val = _has_right_val;
         size = _size;
      }

      public void add(Rect r) {
         rects.add(r);
      }

   }

   public static class Rect{
      final int id; // we use this to access from global parallel arrays

      final int x1, x2, y1, y2;

      final float weight;

      public Rect(int _id, int _x1, int _x2, int _y1, int _y2, float _weight) {
         id = _id;
         x1 = _x1;
         x2 = _x2;
         y1 = _y1;
         y2 = _y2;
         weight = _weight;
      }
   }

   final static List<Feature> feature_instances = new ArrayList<Feature>();

   final static int FEATURE_INTS = 5;

   final static int FEATURE_FLOATS = 3;

   static int[] feature_r1r2r3LnRn;

   static float[] feature_LvRvThres;

   static int feature_ids;

   final static int RECT_INTS = 4;

   final static int RECT_FLOATS = 1;

   final static List<Rect> rect_instances = new ArrayList<Rect>();

   static int rect_x1y1x2y2[];

   static float rect_w[];

   static int rect_ids;

   final static List<Stage> stage_instances = new ArrayList<Stage>();

   final static int STAGE_INTS = 2;

   final static int STAGE_FLOATS = 1;

   static int stage_ids;

   static int stage_startEnd[];

   static float stage_thresh[];

   final static int LEFT = 0;

   final static int RIGHT = 1;

   final static List<Tree> tree_instances = new ArrayList<Tree>();

   final static int TREE_INTS = 2;

   static int tree_ids;

   static int tree_startEnd[];

   /** The list of classifiers that the test image should pass to be considered as an image.*/
   int[] stageIds;

   Point size;

   /**Factory method. Builds a detector from an XML file.
    * @param filename The XML file (generated by OpenCV) describing the Haar Cascade.
    * @return The corresponding detector.
    */
   public static Detector create(String filename) {

      org.jdom.Document document = null;
      SAXBuilder sxb = new SAXBuilder();
      try {
         document = sxb.build(new File(filename));
      } catch (Exception e) {
         e.printStackTrace();
      }

      return new Detector(document);

   }

   /** Detector constructor.
    * Builds, from a XML document (i.e. the result of parsing an XML file, the corresponding Haar cascade.
    * @param document The XML document (parsing of file generated by OpenCV) describing the Haar cascade.
    * 
    * http://code.google.com/p/jjil/wiki/ImplementingHaarCascade
    */

   /** Detector constructor.
    * Builds, from a XML document (i.e. the result of parsing an XML file, the corresponding Haar cascade.
    * @param document The XML document (parsing of file generated by OpenCV) describing the Haar cascade.
    * 
    * http://code.google.com/p/jjil/wiki/ImplementingHaarCascade
    */
   public Detector(Document document) {

      List<Stage> stageList = new LinkedList<Stage>();
      Element racine = (Element) document.getRootElement().getChildren().get(0);
      Scanner scanner = new Scanner(racine.getChild("size").getText());
      size = new Point(scanner.nextInt(), scanner.nextInt());
      Iterator it = racine.getChild("stages").getChildren("_").iterator();
      while (it.hasNext()) {
         Element stage = (Element) it.next();
         float thres = Float.parseFloat(stage.getChild("stage_threshold").getText());
         //System.out.println(thres);
         Iterator it2 = stage.getChild("trees").getChildren("_").iterator();
         Stage st = new Stage(Detector.stage_ids++, thres);

         Detector.stage_instances.add(st);

         System.out.println("create stage " + thres);
         while (it2.hasNext()) {
            Element tree = ((Element) it2.next());
            Tree t = new Tree(Detector.tree_ids++, st);

            Detector.tree_instances.add(t);
            Iterator it4 = tree.getChildren("_").iterator();
            while (it4.hasNext()) {
               Element feature = (Element) it4.next();
               float thres2 = Float.parseFloat(feature.getChild("threshold").getText());
               int left_node = -1;
               float left_val = 0;
               boolean has_left_val = false;
               int right_node = -1;
               float right_val = 0;
               boolean has_right_val = false;
               Element e;
               if ((e = feature.getChild("left_val")) != null) {
                  left_val = Float.parseFloat(e.getText());
                  has_left_val = true;
               } else {
                  left_node = Integer.parseInt(feature.getChild("left_node").getText());
                  has_left_val = false;
               }

               if ((e = feature.getChild("right_val")) != null) {
                  right_val = Float.parseFloat(e.getText());
                  has_right_val = true;
               } else {
                  right_node = Integer.parseInt(feature.getChild("right_node").getText());
                  has_right_val = false;
               }
               Feature f = new Feature(Detector.feature_ids++, t, thres2, left_val, left_node, has_left_val, right_val, right_node,
                     has_right_val, size);
               Detector.feature_instances.add(f);
               Iterator it3 = feature.getChild("feature").getChild("rects").getChildren("_").iterator();
               while (it3.hasNext()) {
                  String s = ((Element) it3.next()).getText().trim();
                  //System.out.println(s);

                  String[] tab = s.split(" ");
                  int x1 = Integer.parseInt(tab[0]);
                  int x2 = Integer.parseInt(tab[1]);
                  int y1 = Integer.parseInt(tab[2]);
                  int y2 = Integer.parseInt(tab[3]);
                  float w = Float.parseFloat(tab[4]);

                  Rect r = new Rect(rect_ids++, x1, x2, y1, y2, w);
                  Detector.rect_instances.add(r);
                  f.add(r);

               }

               t.addFeature(f);

            }
            st.addTree(t);

            // System.out.println("Number of nodes in tree " + t.features.size());
         }
         // System.out.println("Number of trees : " + st.trees.size());
         stageList.add(st);

      }

      // now we take the above generated data structure apart and create a data parallel friendly form. 

      stageIds = new int[stageList.size()];
      for (int i = 0; i < stageIds.length; i++) {
         stageIds[i] = stageList.get(i).id;
      }

      Detector.rect_x1y1x2y2 = new int[Detector.rect_ids * Detector.RECT_INTS];
      Detector.rect_w = new float[Detector.rect_ids * Detector.RECT_FLOATS];
      for (int i = 0; i < Detector.rect_ids; i++) {
         Rect r = Detector.rect_instances.get(i);
         Detector.rect_w[i * Detector.RECT_FLOATS + 0] = r.weight;
         Detector.rect_x1y1x2y2[i * Detector.RECT_INTS + 0] = r.x1;
         Detector.rect_x1y1x2y2[i * Detector.RECT_INTS + 1] = r.y1;
         Detector.rect_x1y1x2y2[i * Detector.RECT_INTS + 2] = r.x2;
         Detector.rect_x1y1x2y2[i * Detector.RECT_INTS + 3] = r.y2;
      }

      Detector.feature_r1r2r3LnRn = new int[Detector.feature_ids * Detector.FEATURE_INTS];
      Detector.feature_LvRvThres = new float[Detector.feature_ids * Detector.FEATURE_FLOATS];
      for (int i = 0; i < Detector.feature_ids; i++) {
         Feature f = Detector.feature_instances.get(i);
         Detector.feature_LvRvThres[i * Detector.FEATURE_FLOATS + 0] = f.left_val;
         Detector.feature_LvRvThres[i * Detector.FEATURE_FLOATS + 1] = f.right_val;
         Detector.feature_LvRvThres[i * Detector.FEATURE_FLOATS + 2] = f.threshold;
         Detector.feature_r1r2r3LnRn[i * Detector.FEATURE_INTS + 0] = (f.rects.size() > 0) ? f.rects.get(0).id : -1;
         Detector.feature_r1r2r3LnRn[i * Detector.FEATURE_INTS + 1] = (f.rects.size() > 1) ? f.rects.get(1).id : -1;
         Detector.feature_r1r2r3LnRn[i * Detector.FEATURE_INTS + 2] = (f.rects.size() > 2) ? f.rects.get(2).id : -1;
         Detector.feature_r1r2r3LnRn[i * Detector.FEATURE_INTS + 3] = (f.has_left_val) ? -1 : f.tree.features.get(f.left_node).id;
         Detector.feature_r1r2r3LnRn[i * Detector.FEATURE_INTS + 4] = (f.has_right_val) ? -1 : f.tree.features.get(f.right_node).id;
      }

      Detector.tree_startEnd = new int[Detector.tree_ids * Detector.TREE_INTS];

      for (int i = 0; i < Detector.tree_ids; i++) {
         Tree t = Detector.tree_instances.get(i);
         Detector.tree_startEnd[i * Detector.TREE_INTS + 0] = t.features.get(0).id;
         Detector.tree_startEnd[i * Detector.TREE_INTS + 1] = t.features.get(t.features.size() - 1).id;
      }

      Detector.stage_startEnd = new int[Detector.stage_ids * Detector.STAGE_INTS];
      Detector.stage_thresh = new float[Detector.stage_ids * Detector.STAGE_FLOATS];
      for (int i = 0; i < Detector.stage_ids; i++) {
         Stage t = Detector.stage_instances.get(i);
         Detector.stage_startEnd[i * Detector.STAGE_INTS + 0] = t.trees.get(0).id;
         Detector.stage_startEnd[i * Detector.STAGE_INTS + 1] = t.trees.get(t.trees.size() - 1).id;
         Detector.stage_thresh[i * Detector.STAGE_FLOATS + 0] = t.threshold;
      }

   }

   /** Returns the list of detected objects in an image applying the Viola-Jones algorithm.
    * 
    * The algorithm tests, from sliding windows on the image, of variable size, which regions should be considered as searched objects.
    * Please see Wikipedia for a description of the algorithm.
    * @param file The image file to scan.
    * @param baseScale The initial ratio between the window size and the Haar classifier size (default 2).
    * @param scale_inc The scale increment of the window size, at each step (default 1.25).
    * @param increment The shift of the window at each sub-step, in terms of percentage of the window size.
    * @return the list of rectangles containing searched objects, expressed in pixels.
    */
   public List<Rectangle> getFaces(String file, float baseScale, float scale_inc, float increment, int min_neighbors,
         boolean doCannyPruning) {

      try {
         BufferedImage image = ImageIO.read(new File(file));

         return getFaces(image, baseScale, scale_inc, increment, min_neighbors, doCannyPruning);
      } catch (IOException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }

      return null;

   }

   public List<Rectangle> getFaces(BufferedImage image, float baseScale, float scale_inc, float increment, int min_neighbors,
         boolean doCannyPruning) {

      final List<Rectangle> ret = new ArrayList<Rectangle>();
      final int width = image.getWidth();
      final int height = image.getHeight();
      final float maxScale = (Math.min((width + 0.f) / size.x, (height + 0.0f) / size.y));
      final int[] imagePixels = new int[width * height];
      final int[] grayImage = new int[width * height];
      final int[] img = new int[width * height];
      final int[] squares = new int[width * height];
      final StopWatch timer = new StopWatch();
      System.out.println(image);
      timer.start();
      for (int i = 0; i < width; i++) {
         for (int j = 0; j < height; j++) {
            imagePixels[i + j * width] = image.getRGB(i, j);
         }
      }
      timer.print("imagegrabber");

      timer.start();

      for (int i = 0; i < width; i++) {
         for (int j = 0; j < height; j++) {
            int c = imagePixels[i + j * width];
            int red = (c & 0x00ff0000) >> 16;
            int green = (c & 0x0000ff00) >> 8;
            int blue = c & 0x000000ff;
            int value = (30 * red + 59 * green + 11 * blue) / 100;
            img[i + j * width] = value;
         }
      }
      timer.print("greyscaler");
      timer.start();

      for (int i = 0; i < width; i++) {
         int col = 0;
         int col2 = 0;
         for (int j = 0; j < height; j++) {
            int value = img[i + j * width];
            grayImage[i + j * width] = (i > 0 ? grayImage[i - 1 + j * width] : 0) + col + value;
            squares[i + j * width] = (i > 0 ? squares[i - 1 + j * width] : 0) + col2 + value * value;
            col += value;
            col2 += value * value;
         }
      }

      timer.print("grey and squares");

      int[] canny = null;
      if (doCannyPruning) {
         timer.start();
         canny = getIntegralCanny(img, width, height);
         timer.print("canny pruning");
      }

      boolean simple = false;
      StopWatch faceDetectTimer = new StopWatch("face detection");
      faceDetectTimer.start();
      if (simple) {

         boolean multiThread = true; // true fastest

         if (multiThread) {
            ExecutorService threadPool = Executors.newFixedThreadPool(16);
            boolean inner = false; // false fastest
            if (inner) {
               for (float scale = baseScale; scale < maxScale; scale *= scale_inc) {

                  //  int loops = 0;
                  //  timer.start();
                  int step = (int) (scale * size.x * increment);
                  int size = (int) (scale * this.size.x);
                  for (int i = 0; i < width - size; i += step) {
                     for (int j = 0; j < height - size; j += step) {
                        final int i_final = i;
                        final int j_final = j;
                        final float scale_final = scale;
                        final int size_final = size;
                        Runnable r = new Runnable(){
                           public void run() {

                              boolean pass = true;
                              for (int stageId : stageIds) {
                                 if (!pass(stageId, grayImage, squares, width, height, i_final, j_final, scale_final)) {
                                    pass = false;
                                    //  System.out.println("Failed at Stage " + k);
                                    break;
                                 }
                              }
                              if (pass) {
                                 System.out.println("found!");
                                 synchronized (ret) {
                                    ret.add(new Rectangle(i_final, j_final, size_final, size_final));
                                 }
                              }
                           }
                        };
                        threadPool.execute(r);
                     }
                  }
                  //  timer.print("scale " + scale + " " + loops + " ");
               }
            } else {
               for (float scale = baseScale; scale < maxScale; scale *= scale_inc) {

                  //  int loops = 0;
                  //  timer.start();
                  final int step = (int) (scale * size.x * increment);
                  final int size = (int) (scale * this.size.x);
                  final float scale_final = scale;

                  for (int i = 0; i < width - size; i += step) {
                     final int i_final = i;
                     Runnable r = new Runnable(){
                        public void run() {
                           for (int j = 0; j < height - size; j += step) {

                              int j_final = j;

                              final int size_final = size;

                              boolean pass = true;
                              for (int stageId : stageIds) {
                                 if (!pass(stageId, grayImage, squares, width, height, i_final, j_final, scale_final)) {
                                    pass = false;
                                    //  System.out.println("Failed at Stage " + k);
                                    break;
                                 }
                              }
                              if (pass) {
                                 System.out.println("found!");
                                 synchronized (ret) {
                                    ret.add(new Rectangle(i_final, j_final, size_final, size_final));
                                 }
                              }
                           }

                        }
                     };
                     threadPool.execute(r);
                  }
                  //  timer.print("scale " + scale + " " + loops + " ");

               }
            }
            threadPool.shutdown();
            try {
               threadPool.awaitTermination(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
               // TODO Auto-generated catch block
               e.printStackTrace();
            }
         } else {

            for (float scale = baseScale; scale < maxScale; scale *= scale_inc) {
               int loops = 0;
               timer.start();
               int step = (int) (scale * size.x * increment);
               int size = (int) (scale * this.size.x);
               for (int i = 0; i < width - size; i += step) {
                  for (int j = 0; j < height - size; j += step) {

                     boolean pass = true;
                     for (int stageId : stageIds) {
                        if (!pass(stageId, grayImage, squares, width, height, i, j, scale)) {
                           pass = false;
                           //  System.out.println("Failed at Stage " + k);
                           break;
                        }

                        // System.out.println("-----end----");
                        // if (pass1 != pass2){
                        //  System.out.println("broken!");
                        // }
                        //  pass = !(pass1 | pass2);
                     }
                     if (pass) {
                        System.out.println("pass!");
                        ret.add(new Rectangle(i, j, size, size));
                     }
                  }
               }
               timer.print("scale " + scale + " " + loops + " ");
            }
         }

      } else {

         for (float scale = baseScale; scale < maxScale; scale *= scale_inc) {
            int loops = 0;
            timer.start();
            int step = (int) (scale * size.x * increment);
            int size = (int) (scale * this.size.x);
            for (int i = 0; i < width - size; i += step) {
               for (int j = 0; j < height - size; j += step) {
                  if (doCannyPruning) {
                     int edges_density = canny[i + size + (j + size) * width] + canny[i + (j) * width]
                           - canny[i + (j + size) * width] - canny[i + size + (j) * width];
                     int d = edges_density / size / size;
                     if (d < 20 || d > 100)
                        continue;
                  }
                  boolean pass = true;
                  int k = 0;
                  for (int stageId : stageIds) {
                     if (!pass(stageId, grayImage, squares, width, height, i, j, scale)) {
                        pass = false;
                        //  System.out.println("Failed at Stage " + k);
                        break;
                     }
                     k++;
                  }
                  if (pass) {

                     System.out.println("found!");
                     ret.add(new Rectangle(i, j, size, size));
                  }
               }
            }
            timer.print("scale " + scale + " " + loops + " ");
         }
      }
      faceDetectTimer.stop();
      return merge(ret, min_neighbors);
   }

   private int getLeftOrRight(int featureId, int[] grayImage, int[] squares, int width, int height, int i, int j, float scale) {

      int w = (int) (scale * size.x);
      int h = (int) (scale * size.y);
      double inv_area = 1. / (w * h);
      //System.out.println("w2 : "+w2);
      int total_x = grayImage[i + w + (j + h) * width] + grayImage[i + (j) * width] - grayImage[i + (j + h) * width]
            - grayImage[i + w + (j) * width];
      int total_x2 = squares[i + w + (j + h) * width] + squares[i + (j) * width] - squares[i + (j + h) * width]
            - squares[i + w + (j) * width];
      double moy = total_x * inv_area;
      double vnorm = total_x2 * inv_area - moy * moy;
      vnorm = (vnorm > 1) ? Math.sqrt(vnorm) : 1;
      // System.out.println(vnorm);
      int rect_sum = 0;
      for (int r = 0; r < 3; r++) {
         int rectId = feature_r1r2r3LnRn[featureId * FEATURE_INTS + r];
         if (rectId != -1) {
            // System.out.println("rect " + r + " id " + rectId);
            int x1 = rect_x1y1x2y2[rectId * RECT_INTS + 0];
            int y1 = rect_x1y1x2y2[rectId * RECT_INTS + 1];
            int x2 = rect_x1y1x2y2[rectId * RECT_INTS + 2];
            int y2 = rect_x1y1x2y2[rectId * RECT_INTS + 3];
            float weight = rect_w[rectId * RECT_FLOATS + 0];
            int rx1 = i + (int) (scale * x1);
            int rx2 = i + (int) (scale * (x1 + y1));
            int ry1 = j + (int) (scale * x2);
            int ry2 = j + (int) (scale * (x2 + y2));
            //System.out.println((rx2-rx1)*(ry2-ry1)+" "+r.weight);
            rect_sum += (int) ((grayImage[rx2 + (ry2) * width] - grayImage[rx1 + (ry2) * width] - grayImage[rx2 + (ry1) * width] + grayImage[rx1
                  + (ry1) * width]) * weight);
         }
      }
      // System.out.println(rect_sum);
      double rect_sum2 = rect_sum * inv_area;

      // System.out.println(rect_sum2+" "+ Feature.LvRvThres[featureId * Feature.FLOATS + 2]*vnorm);  

      return (rect_sum2 < feature_LvRvThres[featureId * FEATURE_FLOATS + 2] * vnorm) ? LEFT : RIGHT;

   }

   private boolean pass(int stageId, int[] grayImage, int[] squares, int width, int height, int i, int j, float scale) {

      float sum = 0;
      for (int treeId = stage_startEnd[stageId * STAGE_INTS + 0]; treeId <= stage_startEnd[stageId * STAGE_INTS + 1]; treeId++) {

         //  System.out.println("stage id " + stageId + "  tree id" + treeId);
         int featureId = tree_startEnd[treeId * TREE_INTS + 0];
         float thresh = 0f;
         boolean done = false;
         while (!done) {
            //  System.out.println("feature id "+featureId);
            int where = getLeftOrRight(featureId, grayImage, squares, width, height, i, j, scale);
            if (where == LEFT) {
               int leftNodeId = feature_r1r2r3LnRn[featureId * FEATURE_INTS + 3];
               if (leftNodeId == -1) {
                  //  System.out.println("left-val");
                  thresh = feature_LvRvThres[featureId * FEATURE_FLOATS + 0];
                  done = true;
               } else {
                  // System.out.println("left");
                  featureId = leftNodeId;
               }
            } else {
               int rightNodeId = feature_r1r2r3LnRn[featureId * FEATURE_INTS + 4];
               if (rightNodeId == -1) {
                  // System.out.println("right-val");
                  thresh = feature_LvRvThres[featureId * FEATURE_FLOATS + 1];
                  done = true;
               } else {
                  //  System.out.println("right");
                  featureId = rightNodeId;
               }
            }
         }

         sum += thresh;
      }
      //System.out.println(sum+" "+threshold);

      return sum > stage_thresh[stageId * STAGE_FLOATS + 0];
   }

   public int[] getIntegralCanny(int[] grayImage, int width, int height) {

      int[] canny = new int[grayImage.length];
      final StopWatch timer = new StopWatch();
      timer.start();
      for (int i = 2; i < width - 2; i++) {
         for (int j = 2; j < height - 2; j++) {
            int sum = 0;
            sum += 2 * grayImage[i - 2 + (j - 2) * width];
            sum += 4 * grayImage[i - 2 + (j - 1) * width];
            sum += 5 * grayImage[i - 2 + (j + 0) * width];
            sum += 4 * grayImage[i - 2 + (j + 1) * width];
            sum += 2 * grayImage[i - 2 + (j + 2) * width];
            sum += 4 * grayImage[i - 1 + (j - 2) * width];
            sum += 9 * grayImage[i - 1 + (j - 1) * width];
            sum += 12 * grayImage[i - 1 + (j + 0) * width];
            sum += 9 * grayImage[i - 1 + (j + 1) * width];
            sum += 4 * grayImage[i - 1 + (j + 2) * width];
            sum += 5 * grayImage[i + 0 + (j - 2) * width];
            sum += 12 * grayImage[i + 0 + (j - 1) * width];
            sum += 15 * grayImage[i + 0 + (j + 0) * width];
            sum += 12 * grayImage[i + 0 + (j + 1) * width];
            sum += 5 * grayImage[i + 0 + (j + 2) * width];
            sum += 4 * grayImage[i + 1 + (j - 2) * width];
            sum += 9 * grayImage[i + 1 + (j - 1) * width];
            sum += 12 * grayImage[i + 1 + (j + 0) * width];
            sum += 9 * grayImage[i + 1 + (j + 1) * width];
            sum += 4 * grayImage[i + 1 + (j + 2) * width];
            sum += 2 * grayImage[i + 2 + (j - 2) * width];
            sum += 4 * grayImage[i + 2 + (j - 1) * width];
            sum += 5 * grayImage[i + 2 + (j + 0) * width];
            sum += 4 * grayImage[i + 2 + (j + 1) * width];
            sum += 2 * grayImage[i + 2 + (j + 2) * width];

            canny[i + j * width] = sum / 159;
            //System.out.println(canny[i][j]);
         }
      }
      timer.print("canny convolution");
      timer.start();
      int[] grad = new int[grayImage.length];
      for (int i = 1; i < width - 1; i++) {
         for (int j = 1; j < height - 1; j++) {
            int grad_x = -canny[i - 1 + (j - 1) * width] + canny[i + 1 + (j - 1) * width] - 2 * canny[i - 1 + (j) * width] + 2
                  * canny[i + 1 + (j) * width] - canny[i - 1 + (j + 1) * width] + canny[i + 1 + (j + 1) * width];
            int grad_y = canny[i - 1 + (j - 1) * width] + 2 * canny[i + (j - 1) * width] + canny[i + 1 + (j - 1) * width]
                  - canny[i - 1 + (j + 1) * width] - 2 * canny[i + (j + 1) * width] - canny[i + 1 + (j + 1) * width];
            grad[i + j * width] = Math.abs(grad_x) + Math.abs(grad_y);
            //System.out.println(grad[i][j]);
         }
      }
      timer.print("canny convolution 2");
      timer.start();
      //JFrame f = new JFrame();
      //f.setContentPane(new DessinChiffre(grad));
      //f.setVisible(true);
      for (int i = 0; i < width; i++) {
         int col = 0;
         for (int j = 0; j < height; j++) {
            int value = grad[i + j * width];
            canny[i + j * width] = (i > 0 ? canny[i - 1 + j * width] : 0) + col + value;
            col += value;
         }
      }
      timer.print("canny convolution 3");
      return canny;

   }

   public List<java.awt.Rectangle> merge(List<java.awt.Rectangle> rects, int min_neighbors) {

      List<java.awt.Rectangle> retour = new LinkedList<java.awt.Rectangle>();
      int[] ret = new int[rects.size()];
      int nb_classes = 0;
      for (int i = 0; i < rects.size(); i++) {
         boolean found = false;
         for (int j = 0; j < i; j++) {
            if (equals(rects.get(j), rects.get(i))) {
               found = true;
               ret[i] = ret[j];
            }
         }
         if (!found) {
            ret[i] = nb_classes;
            nb_classes++;
         }
      }
      //System.out.println(Arrays.toString(ret));
      int[] neighbors = new int[nb_classes];
      Rectangle[] rect = new Rectangle[nb_classes];
      for (int i = 0; i < nb_classes; i++) {
         neighbors[i] = 0;
         rect[i] = new Rectangle(0, 0, 0, 0);
      }
      for (int i = 0; i < rects.size(); i++) {
         neighbors[ret[i]]++;
         rect[ret[i]].x += rects.get(i).x;
         rect[ret[i]].y += rects.get(i).y;
         rect[ret[i]].height += rects.get(i).height;
         rect[ret[i]].width += rects.get(i).width;
      }
      for (int i = 0; i < nb_classes; i++) {
         int n = neighbors[i];
         if (n >= min_neighbors) {
            Rectangle r = new Rectangle(0, 0, 0, 0);
            r.x = (rect[i].x * 2 + n) / (2 * n);
            r.y = (rect[i].y * 2 + n) / (2 * n);
            r.width = (rect[i].width * 2 + n) / (2 * n);
            r.height = (rect[i].height * 2 + n) / (2 * n);
            retour.add(r);
         }
      }

      return retour;

   }

   public boolean equals(Rectangle r1, Rectangle r2) {

      int distance = (int) (r1.width * 0.2);

      if (r2.x <= r1.x + distance && r2.x >= r1.x - distance && r2.y <= r1.y + distance && r2.y >= r1.y - distance
            && r2.width <= (int) (r1.width * 1.2) && (int) (r2.width * 1.2) >= r1.width) {

         return true;
      }
      if (r1.x >= r2.x && r1.x + r1.width <= r2.x + r2.width && r1.y >= r2.y && r1.y + r1.height <= r2.y + r2.height) {

         return true;
      }

      return false;

   }
}
