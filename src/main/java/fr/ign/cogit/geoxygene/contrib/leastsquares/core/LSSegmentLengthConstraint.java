/*******************************************************************************
 * This software is released under the licence CeCILL
 * 
 * see Licence_CeCILL-C_fr.html see Licence_CeCILL-C_en.html
 * 
 * see <a href="http://www.cecill.info/">http://www.cecill.info/a>
 * 
 * @copyright IGN
 ******************************************************************************/
package fr.ign.cogit.geoxygene.contrib.leastsquares.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;

import fr.ign.cogit.geoxygene.api.feature.IFeature;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.IDirectPosition;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.ILineString;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.IPolygon;
import fr.ign.cogit.geoxygene.api.spatial.geomroot.IGeometry;
import fr.ign.cogit.geoxygene.util.algo.geometricAlgorithms.LineDensification;

/**
 * @author G. Touya
 * 
 *         Constraint that preserves the length of a segment.
 */
public class LSSegmentLengthConstraint extends LSInternalConstraint {

  private Double minSegLength;
  /**
   * Below 1.5 m, the weight of the constraint is amplified to avoid angle
   * deformation of such small segments.
   */
  private double thresholdMinLength = 6.0;

  /**
   * True if the constraint is applicable on point.
   * @param point
   * @return
   */
  public static boolean appliesTo(LSPoint point) {
    if (point.isFixed())
      return false;
    if (!point.isPointIniFin()) {
      return true;
    }
    return false;
  }

  public LSSegmentLengthConstraint(LSPoint pt, LSScheduler scheduler) {
    super(pt, scheduler);
  }

  /*
   * (non-Javadoc)
   * 
   * @seefr.ign.gothic.cogit.guillaume.moindresCarres.ContrainteInterneMC#
   * calculeSystemeEquations(gothic.main.GothicObject,
   * fr.ign.gothic.cogit.guillaume.moindresCarres.MCPoint)
   */
  @Override
  public EquationsSystem calculeSystemeEquations(IFeature obj, LSPoint point) {

    EquationsSystem systeme = this.sched.initSystemeLocal();
    // on commence par r??cup??rer le point pr??c??dent et le suivant
    IDirectPosition coordPrec = null;
    IDirectPosition coordSuiv = null;
    // on commence par r??cup??rer la g??om??trie
    IGeometry geom = obj.getGeom();
    ILineString ligne;
    // test if densification is required
    if (geom.coord().size() < this.sched.getMapObjPts().get(obj).size()) {
      if (geom instanceof ILineString) {
        ligne = LineDensification.densification2((ILineString) geom, sched
            .getMapspec().getDensStep());
      } else {
        ligne = LineDensification.densification2(((IPolygon) geom)
            .exteriorLineString(), sched.getMapspec().getDensStep());
      }
    } else {
      if (geom instanceof ILineString) {
        ligne = (ILineString) geom;
      } else {
        ligne = ((IPolygon) geom).exteriorLineString();
      }
    }

    for (int i = 0; i < ligne.numPoints(); i++) {
      IDirectPosition coord = ligne.coord().get(i);
      if (!coord.equals2D(point.getIniPt(), 0.01)) {
        continue;
      }

      // si on est l??, c'est le bon vertex
      // on marque le vertex pr??c??dent
      int prevIndex, nextIndex;
      if (i == 0) {
        prevIndex = ligne.numPoints() - 2;
      } else {
        prevIndex = i - 1;
      }
      // on marque le vertex suivant
      if (i + 1 == ligne.numPoints()) {
        nextIndex = 0;
      } else {
        nextIndex = i + 1;
      }
      // on r??cup??re les coordonn??es pr??c??dentes
      coordPrec = ligne.coord().get(prevIndex);
      // on r??cup??re les coordonn??es suivantes
      coordSuiv = ligne.coord().get(nextIndex);
      break;
    }

    // on r??cup??re maintenant les MCPoints correspondant ?? ces coordonn??es
    ArrayList<LSPoint> setPoints = this.sched.getMapObjPts().get(obj);
    LSPoint pointPrec = null, pointSuiv = null;
    Iterator<LSPoint> iter = setPoints.iterator();
    while (iter.hasNext()) {
      LSPoint pt = iter.next();
      if (pt.getIniPt().equals2D(coordPrec, 0.01)) {
        pointPrec = pt;
      }
      if (pt.getIniPt().equals2D(coordSuiv, 0.01)) {
        pointSuiv = pt;
      }
    }// while boucle sur setPoints

    // construction du vecteur des inconnues
    systeme.setUnknowns(new Vector<LSPoint>());
    if (!pointPrec.isFixed()) {
      systeme.getUnknowns().addElement(pointPrec);
      systeme.getUnknowns().addElement(pointPrec);
      minSegLength = point.getIniPt().distance2D(coordPrec);
    }
    systeme.getUnknowns().addElement(point);
    systeme.getUnknowns().addElement(point);
    if (!pointSuiv.isFixed()) {
      systeme.getUnknowns().addElement(pointSuiv);
      systeme.getUnknowns().addElement(pointSuiv);
      if (minSegLength != null)
        minSegLength = Math.min(minSegLength,
            point.getIniPt().distance2D(coordSuiv));
      else
        minSegLength = point.getIniPt().distance2D(coordSuiv);
    }

    // construction du vecteur des contraintes
    systeme.setConstraints(new Vector<LSConstraint>());
    systeme.getConstraints().add(this);
    systeme.getConstraints().add(this);

    // construction de la matrice des observations
    // c'est une matrice (4,1) contenant deux 0
    systeme.initObservations(2);

    double normeU = Math.sqrt((point.getIniPt().getX() - pointPrec.getIniPt()
        .getX())
        * (point.getIniPt().getX() - pointPrec.getIniPt().getX())
        + (point.getIniPt().getY() - pointPrec.getIniPt().getY())
        * (point.getIniPt().getY() - pointPrec.getIniPt().getY()));
    double normeW = Math.sqrt((pointSuiv.getIniPt().getX() - point.getIniPt()
        .getX())
        * (pointSuiv.getIniPt().getX() - point.getIniPt().getX())
        + (pointSuiv.getIniPt().getY() - point.getIniPt().getY())
        * (pointSuiv.getIniPt().getY() - point.getIniPt().getY()));

    // calcul des facteurs pour les ??quations sur les longueurs
    double a1 = 0.0, b1 = 0.0, c1 = 0.0, d1 = 0.0;
    double a2 = 0.0, b2 = 0.0, c2 = 0.0, d2 = 0.0;
    a1 = (pointPrec.getIniPt().getX() - point.getIniPt().getX()) / normeU;
    b1 = (pointPrec.getIniPt().getY() - point.getIniPt().getY()) / normeU;
    c1 = (point.getIniPt().getX() - pointPrec.getIniPt().getX()) / normeU;
    d1 = (point.getIniPt().getY() - pointPrec.getIniPt().getY()) / normeU;
    a2 = (pointSuiv.getIniPt().getX() - point.getIniPt().getX()) / normeW;
    b2 = (pointSuiv.getIniPt().getY() - point.getIniPt().getY()) / normeW;
    c2 = (point.getIniPt().getX() - pointSuiv.getIniPt().getX()) / normeW;
    d2 = (point.getIniPt().getY() - pointSuiv.getIniPt().getY()) / normeW;

    // construction de la matrice A
    if (pointPrec.isFixed() && pointSuiv.isFixed()) {
      systeme.initMatriceA(2, 2);
      systeme.setA(0, 0, c1);
      systeme.setA(0, 1, d1);
      systeme.setA(1, 0, c2);
      systeme.setA(1, 1, d2);
      systeme.setNonNullValues(4);
      systeme.setObs(0,
          -a1 * pointPrec.getDeltaX() - b1 * pointPrec.getDeltaY());
      systeme.setObs(1,
          -a2 * pointSuiv.getDeltaX() - b2 * pointSuiv.getDeltaY());
    } else if (pointPrec.isFixed()) {
      systeme.initMatriceA(2, 4);
      systeme.setA(0, 0, c1);
      systeme.setA(0, 1, d1);
      systeme.setA(1, 0, c2);
      systeme.setA(1, 1, d2);
      systeme.setA(1, 2, a2);
      systeme.setA(1, 3, b2);
      systeme.setNonNullValues(6);
      systeme.setObs(0,
          -a1 * pointPrec.getDeltaX() - b1 * pointPrec.getDeltaY());
    } else if (pointSuiv.isFixed()) {
      systeme.initMatriceA(2, 4);
      systeme.setA(0, 0, a1);
      systeme.setA(0, 1, b1);
      systeme.setA(0, 2, c1);
      systeme.setA(0, 3, d1);
      systeme.setA(1, 2, c2);
      systeme.setA(1, 3, d2);
      systeme.setNonNullValues(6);
      systeme.setObs(1,
          -a2 * pointSuiv.getDeltaX() - b2 * pointSuiv.getDeltaY());
    } else {
      systeme.initMatriceA(2, 6);
      systeme.setA(0, 0, a1);
      systeme.setA(0, 1, b1);
      systeme.setA(0, 2, c1);
      systeme.setA(0, 3, d1);
      systeme.setA(1, 4, a2);
      systeme.setA(1, 5, b2);
      systeme.setA(1, 2, c2);
      systeme.setA(1, 3, d2);
      systeme.setNonNullValues(8);
    }

    return systeme;
  }

  @Override
  public double getWeightFactor() {
    if (minSegLength < thresholdMinLength)
      return super.getWeightFactor() * 10.0;
    return super.getWeightFactor();
  }

}
