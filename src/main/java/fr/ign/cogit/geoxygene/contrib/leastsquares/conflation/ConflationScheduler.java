package fr.ign.cogit.geoxygene.contrib.leastsquares.conflation;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import fr.ign.cogit.geoxygene.api.feature.IFeature;
import fr.ign.cogit.geoxygene.api.feature.IFeatureCollection;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.IDirectPosition;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.ILineString;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.IPolygon;
import fr.ign.cogit.geoxygene.api.spatial.geomprim.IPoint;
import fr.ign.cogit.geoxygene.api.spatial.geomroot.IGeometry;
import fr.ign.cogit.geoxygene.contrib.leastsquares.core.LSPoint;
import fr.ign.cogit.geoxygene.contrib.leastsquares.core.LSScheduler;
import fr.ign.cogit.geoxygene.contrib.leastsquares.core.MapspecsLS;
import fr.ign.cogit.geoxygene.feature.DefaultFeature;
import fr.ign.cogit.geoxygene.feature.FT_FeatureCollection;
import fr.ign.cogit.geoxygene.generalisation.Filtering;
import fr.ign.cogit.geoxygene.util.algo.geometricAlgorithms.CommonAlgorithmsFromCartAGen;
import fr.ign.cogit.geoxygene.util.algo.geometricAlgorithms.LineDensification;
import fr.ign.cogit.geoxygene.util.algo.geomstructure.Vector2D;

public class ConflationScheduler extends LSScheduler {

    private static Logger logger = Logger
            .getLogger(ConflationScheduler.class.getName());

    private IFeatureCollection<DefaultFeature> loadedVectors = new FT_FeatureCollection<DefaultFeature>();
    private Class<? extends LSVectorDisplConstraint> conflationConstraint;

    public ConflationScheduler(MapspecsLS ms, Set<DefaultFeature> vectors,
            Class<? extends LSVectorDisplConstraint> conflationConstraint) {
        super(ms);
        this.loadedVectors.addAll(vectors);
        this.conflationConstraint = conflationConstraint;
    }

    @Override
    public void triggerAdjustment(EndVertexStrategy strategy, boolean commit) {
        // on commence par s??lectionner les objets
        logger.fine("Moindres carres : on recupere les objets");
        try {
            this.setObjs();
        } catch (IllegalArgumentException e2) {
            e2.printStackTrace();
        } catch (SecurityException e2) {
            e2.printStackTrace();
        } catch (IllegalAccessException e2) {
            e2.printStackTrace();
        } catch (NoSuchFieldException e2) {
            e2.printStackTrace();
        } catch (ClassNotFoundException e2) {
            e2.printStackTrace();
        }
        if (this.countObjs() == 0) {
            logger.fine("Moindres carres : pas d objet a traiter");
            return;
        }
        logger.fine(
                "Moindres carres : " + this.countObjs() + " objets a traiter");

        // on cr??e les LSPoints de chaque objet
        logger.fine("Moindres carres : on initialise les points");
        try {
            this.initialiserLSPoints();
        } catch (SecurityException e1) {
            e1.printStackTrace();
        } catch (IllegalArgumentException e1) {
            e1.printStackTrace();
        } catch (ClassNotFoundException e1) {
            e1.printStackTrace();
        } catch (NoSuchMethodException e1) {
            e1.printStackTrace();
        } catch (IllegalAccessException e1) {
            e1.printStackTrace();
        } catch (InvocationTargetException e1) {
            e1.printStackTrace();
        } catch (InstantiationException e1) {
            e1.printStackTrace();
        }

        // puis on initialise les contraintes internes
        logger.fine("Moindres carres : on initialise les contraintes externes");
        try {
            this.initialiserContraintesExternes();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // puis on initialise les contraintes internes
        logger.fine(
                "Moindres carres : on initialise les contraintes de conflation");
        try {
            this.initialiseConflationConstraints();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

        // on assemble alors le syst??me d'??quations
        logger.fine("Moindres carres : on assemble les systemes d equation");
        this.assembleSystemesEquation();

        // puis on r??alise l'ajustement du syst??me par moindres carr??s
        logger.fine("Moindres carres : on fait l ajustement");
        this.systemeGlobal.ajustementMoindresCarres(
                this.getMapspec().getPoidsContraintes());

        logger.finer(
                "solutions : " + this.systemeGlobal.getSolutions().toString());

        // enfin, on met ?? jour les g??om??tries
        logger.fine("Moindres carres : on met a jour les geometries");
        this.majGeometries(commit);

    }

    protected void initialiseConflationConstraints()
            throws ClassNotFoundException, SecurityException,
            NoSuchMethodException, IllegalArgumentException,
            InstantiationException, IllegalAccessException,
            InvocationTargetException {
        IFeatureCollection<IFeature> conflatedFeats = new FT_FeatureCollection<IFeature>();
        conflatedFeats.addAll(this.getObjsRigides());
        conflatedFeats.addAll(this.getObjsMalleables());
        // loop on the vector features
        for (DefaultFeature vectFeat : loadedVectors) {
            LSPoint iniPt = this
                    .getPointFromCoord(vectFeat.getGeom().coord().get(0));
            // convert the feature into a Vector2D
            Vector2D vect = new Vector2D(vectFeat.getGeom().coord().get(0),
                    vectFeat.getGeom().coord().get(1));
            // on cherche les objets les plus proches
            Collection<IFeature> querySet = conflatedFeats.select(
                    vectFeat.getGeom().coord().get(0), 5 * vect.norme());

            // loop on the close features
            for (IFeature feat : querySet) {
                if (feat == null)
                    continue;

                // on cherche le LSPoint d'objet le plus proche de pt
                double minDist = 5 * vect.norme();
                for (LSPoint point : this.getMapObjPts().get(feat)) {
                    double distance = vectFeat.getGeom().coord().get(0)
                            .distance2D(point.getIniPt());
                    if (distance < minDist && distance > 0.0) {
                        // on construit un objet VecteurDeplacement
                        DisplacementVector vector = new DisplacementVector(
                                iniPt, point, vect);
                        // on construit une nouvelle contrainte ?? partir de ce
                        // vecteur
                        Constructor<? extends LSVectorDisplConstraint> constr = conflationConstraint
                                .getConstructor(LSPoint.class, IFeature.class,
                                        IFeature.class, LSScheduler.class,
                                        DisplacementVector.class);
                        LSVectorDisplConstraint contrainte = constr.newInstance(
                                point, vectFeat, feat, this, vector);
                        point.getExternalConstraints().add(contrainte);
                    }
                }
            }
        }
    }

    /**
     * En plus de l'initialisation classique, cette m??thode ajoute des points au
     * d??part des vecteurs de conflation. {@inheritDoc}
     * <p>
     * 
     */
    @Override
    protected void initialiserLSPoints() throws SecurityException,
            IllegalArgumentException, ClassNotFoundException,
            NoSuchMethodException, IllegalAccessException,
            InvocationTargetException, InstantiationException {
        Set<IFeature> objs = new HashSet<IFeature>();
        objs.addAll(this.getObjsFixes());
        objs.addAll(this.getObjsMalleables());
        objs.addAll(this.getObjsRigides());
        Set<LSPoint> points = new HashSet<LSPoint>();

        Iterator<IFeature> iter = objs.iterator();
        while (iter.hasNext()) {
            IFeature obj = iter.next();

            // on construit le set des LSPoint de obj
            ArrayList<LSPoint> listePoints = new ArrayList<LSPoint>();

            // on r??cup??re la g??om??trie de obj
            IGeometry geom = obj.getGeom();
            // si l'objet est de type mall??able, on le densifie ?? un pas de 50 m
            if (this.getObjsMalleables().contains(obj)) {
                geom = LineDensification.densification2(geom,
                        this.getMapspec().getDensStep());
            }

            // cas d'un point
            if (geom instanceof IPoint) {
                LSPoint point = this.construirePoint(obj,
                        ((IPoint) geom).getPosition(), 1, GeometryType.POINT,
                        true, this.getSymbolWidth(obj), points);
                point.setContraintesInternes(this.getMapspec(), this);
                listePoints.add(point);
                points.add(point);
                this.getMapObjPts().put(obj, listePoints);
            } else if (geom instanceof ILineString) {
                // cas d'une ligne
                // on marque la ligne
                int position = 1;
                boolean extreme = true;
                for (IDirectPosition vertex : geom.coord()) {
                    // on construit le nouveau point
                    LSPoint point = this.construirePoint(obj, vertex, position,
                            GeometryType.LINE, extreme,
                            this.getSymbolWidth(obj), points);
                    // on lui d??finit ses contraintes internes
                    point.setContraintesInternes(this.getMapspec(), this);
                    points.add(point);
                    listePoints.add(point);
                    // on passe au suivant
                    position += 1;
                    if (position < geom.numPoints()) {
                        extreme = false;
                    } else if (position == geom.numPoints()) {
                        extreme = true;
                    }
                }
                this.getMapObjPts().put(obj, listePoints);
            } else {
                // cas d'une surface, on r??cup??re le outer ring
                ILineString ring = ((IPolygon) geom).exteriorLineString();
                // on marque la ligne
                int position = 1;
                for (IDirectPosition vertex : ring.coord()) {
                    // on r??cup??re les coordonn??es du vertex marqu??
                    LSPoint point = this.construirePoint(obj, vertex, position,
                            GeometryType.POLYGON, false,
                            this.getSymbolWidth(obj), points);
                    point.setContraintesInternes(this.getMapspec(), this);
                    points.add(point);
                    listePoints.add(point);
                    position += 1.0;
                } // for i
                this.getMapObjPts().put(obj, listePoints);
            }

            // gestion des vecteurs
            Collection<DefaultFeature> vectors = loadedVectors
                    .select(geom.buffer(0.01));
            for (DefaultFeature vector : vectors) {
                // on v??rifie s'il y a bien un point au niveau du point initial
                // du
                // vecteur
                IDirectPosition ini = vector.getGeom().coord().get(0);
                boolean created = false;
                for (LSPoint lspt : listePoints) {
                    if (lspt.getIniPt().equals(ini)) {
                        created = true;
                        lspt.setFixed(true);
                        lspt.setFinalPt(vector.getGeom().coord().get(1));
                        break;
                    }
                }

                // s'il n'a pas ??t?? cr????, il faut l'ajouter au bon endroit
                if (!created) {
                    if (geom instanceof ILineString) {
                        ILineString newLine = CommonAlgorithmsFromCartAGen
                                .insertVertex((ILineString) geom, ini);
                        obj.setGeom(newLine);
                        int index = newLine.coord().getList().indexOf(ini);
                        if (index == -1) {
                            // the vector is not on the line but some
                            // millimeters aside
                            // there is no need to add a LSPoint in the line
                            continue;
                        }
                        // on construit le nouveau point
                        LSPoint lsPt = this.construirePoint(obj, ini, index,
                                GeometryType.LINE, false,
                                this.getSymbolWidth(obj), points);
                        lsPt.setFixed(true);
                        lsPt.setFinalPt(vector.getGeom().coord().get(1));

                        // on lui d??finit ses contraintes internes
                        lsPt.setContraintesInternes(this.getMapspec(), this);
                        points.add(lsPt);
                        listePoints.add(index, lsPt);
                        this.getMapObjPts().put(obj, listePoints);
                    } else if (geom instanceof IPolygon) {
                        IPolygon newLine = CommonAlgorithmsFromCartAGen
                                .insertVertex((IPolygon) geom, ini);
                        obj.setGeom(newLine);
                        int index = newLine.coord().getList().indexOf(ini);
                        if (index == -1) {
                            // the vector is not on the line but some
                            // millimeters aside
                            // there is no need to add a LSPoint in the line
                            continue;
                        }
                        // on construit le nouveau point
                        LSPoint lsPt = this.construirePoint(obj, ini, index,
                                GeometryType.POLYGON, false,
                                this.getSymbolWidth(obj), points);
                        lsPt.setFixed(true);
                        lsPt.setFinalPt(vector.getGeom().coord().get(1));

                        // on lui d??finit ses contraintes internes
                        lsPt.setContraintesInternes(this.getMapspec(), this);
                        points.add(lsPt);
                        listePoints.add(index, lsPt);
                        this.getMapObjPts().put(obj, listePoints);
                    }
                }
            }
        } // boucle sur les objets ?? du scheduler
    }

    /**
     * Par rapport ?? la version g??n??rique, on r??alise un filtrage sur les
     * g??om??tries si c'est demand?? dans les mapspecs. {@inheritDoc}
     * <p>
     * 
     */
    @Override
    protected void majGeometries(boolean commit) {
        // on "map" les inconnues avec les solutions
        Map<LSPoint, IDirectPosition> mapInconnues = this.setMapInconnues();
        // on commence par parcourir la map des objets trait??s
        for (IFeature obj : this.getMapObjPts().keySet()) {
            // on r??cup??re la g??om??trie de obj
            IGeometry geomIni = obj.getGeom();
            // on r??cup??re le type de la g??om??trie
            IGeometry geomFin;
            // suivant le type de la g??om??trie, on construit la g??om??trie
            // finale appropri??e (point, ligne ou surface)
            if (geomIni instanceof IPoint) {
                geomFin = this.construitNouveauPoint(obj, (IPoint) geomIni,
                        mapInconnues);
            } else if (geomIni instanceof ILineString) {
                geomFin = this.construitNouvelleLigne(obj, mapInconnues);
            } else {
                geomFin = this.construitNouvelleSurface(obj, (IPolygon) geomIni,
                        mapInconnues);
            }
            if (this.getMapspec().isFilter())
                geomFin = Filtering.DouglasPeucker(geomFin,
                        getMapspec().getFilterThreshold());
            if (commit) {
                // on applique l'ancienne g??om??trie dans l'attribut
                // correspondant
                obj.setGeom(geomFin);
                this.getMapObjGeom().put(obj, geomIni);
            } else {
                // on applique la nouvelle g??om??trie dans l'attribut
                // correspondant
                this.getMapObjGeom().put(obj, geomFin);
            }

        } // while boucle sur les cl??s de mapObjPts
    }
}
