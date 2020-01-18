
import com.jme3.animation.AnimChannel;
import com.jme3.animation.AnimControl;
import com.jme3.animation.AnimEventListener;
import com.jme3.animation.LoopMode;
import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.control.CharacterControl;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.cinematic.MotionPath;
import com.jme3.cinematic.MotionPathListener;
import com.jme3.cinematic.events.MotionEvent;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.font.BitmapText;
import com.jme3.input.ChaseCamera;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Quad;
import com.jme3.system.AppSettings;
import com.jme3.system.Timer;
import com.jme3.texture.Texture;
import com.jme3.ui.Picture;
import com.jme3.util.SkyFactory;
import com.jme3.water.SimpleWaterProcessor;
import java.awt.Rectangle;

public class Main extends SimpleApplication implements ActionListener, AnimEventListener {

    //Physics
    private BulletAppState bulletAppState;
    //Scene
    private Node scene;
    //Water
    SimpleWaterProcessor waterProcessor;
    //Character
    private Node model;
    private CharacterControl character;
    private Vector3f walkDirection = new Vector3f(0, 0, 0);//stopping character
    private float airTime = 0;
    //Monkey
    private Node monkey;
    private CharacterControl monkeyControl;
    //Fish
    private Node fish;
    private MotionPath path;
    private MotionEvent motionControl;
    private float fishJumpTimer;
    private boolean active = true;
    //Door
    private RigidBodyControl door_phys;
    private Node door;
    private boolean attempted;
    //Animation
    private AnimControl animationControl;
    private AnimChannel animationChannel; //add more channels for simultaneous actions
    private boolean left = false, right = false, up = false, down = false;
    //Camera
    private ChaseCamera chaseCam;
    //Mouse Picking
    Node clickables;

    public static void main(String[] args) {
        Main app = new Main();
        app.start();
    }

    @Override
    public void simpleInitApp() {
        setDisplayFps(false);       // to hide the FPS
        setDisplayStatView(false);  // to hide the statistics 

        clickables = new Node("Clickables");
        rootNode.attachChild(clickables);

        loadPhysics();
        loadScene();
        loadSky();
        loadCharacter();
        loadDoor();
        loadWaterProcessor();
        loadRiver();
        loadFish();
        loadWaterFall();
        addWaterProcessor();
        loadLightSource();
        startAnimation();
        loadFlyCam();
        registerInput();


        Picture pic = new Picture("HUD Picture");
        pic.setImage(assetManager, "Interface/Chat_Box.png", true);
        pic.setWidth(300);
        pic.setHeight(100);
        pic.setPosition(0, 0);
        guiNode.attachChild(pic);

        BitmapText hudText = new BitmapText(guiFont, false);
        hudText.setSize(guiFont.getCharSet().getRenderedSize());      // font size
        hudText.setColor(ColorRGBA.Blue);                             // font color
        hudText.setText("Character Chat");             // the text
        hudText.setLocalTranslation(100, 50, 0); // position
        guiNode.attachChild(hudText);
    }

    public void simpleUpdate(float tpf) {
        // The use of multLocal here is to control the rate of movement multiplier for character walk speed. 
        Vector3f camDir = cam.getDirection().clone().multLocal(0.45f);
        Vector3f camLeft = cam.getLeft().clone().multLocal(0.45f);

        //Fish jump timer
        fishJumpTimer += tpf;
        if (fishJumpTimer > 3 && active == true) {
            motionControl.pause();
            active = false;
            fishJumpTimer = 0;
            System.out.println(fishJumpTimer);
        } else if (fishJumpTimer > 10 && active == false) {
            motionControl.play();
            active = true;

            fishJumpTimer = 0;

        }

        camDir.y = 0;
        camLeft.y = 0;

        walkDirection.set(
                0, 0, 0);

        if (left) {
            walkDirection.addLocal(camLeft);
        }
        if (right) {
            walkDirection.addLocal(camLeft.negate());
        }
        if (up) {
            walkDirection.addLocal(camDir);
        }
        if (down) {
            walkDirection.addLocal(camDir.negate());
        }

        if (!character.onGround()) { // use !character.isOnGround() if the character is a BetterCharacterControl type.
            airTime += tpf;
        } else {
            airTime = 0;
        }

        if (walkDirection.lengthSquared()
                == 0) { //Use lengthSquared() (No need for an extra sqrt())
            if (!"stand".equals(animationChannel.getAnimationName())) {
                animationChannel.setAnim("stand", 1f);
            }
        } else {
            character.setViewDirection(walkDirection);
            if (airTime > .3f) {
                if (!"stand".equals(animationChannel.getAnimationName())) {
                    animationChannel.setAnim("stand");
                }
            } else if (!"Walk".equals(animationChannel.getAnimationName())) {
                animationChannel.setAnim("Walk", 0.7f);
            }
        }

        character.setWalkDirection(walkDirection); // THIS IS WHERE THE WALKING HAPPENS
    }

    public void onAction(String name, boolean isPressed, float tpf) {

        //resets the result list
        CollisionResults results = new CollisionResults();
        //Translate the 2d click into the 3d world
        Vector2f click2d = inputManager.getCursorPosition();
        Vector3f click3d = cam.getWorldCoordinates(
                new Vector2f(click2d.x, click2d.y), 0f).clone();
        Vector3f dir = cam.getWorldCoordinates(
                new Vector2f(click2d.x, click2d.y), 1f).subtractLocal(click3d).normalizeLocal();
        Ray ray = new Ray(click3d, dir);
        clickables.collideWith(ray, results);
        //This holds the name of the Geom being targeted
        String hit = "";
        //Holds the distance of world units between character and clickable object
        float dist = 0;


        if (name.equals("Click") && attempted == false && !isPressed) {
            for (int i = 0; i < results.size(); i++) {
                // For each hit, we know distance, impact point, name of geometry.
                dist = results.getCollision(i).getDistance();
                hit = results.getCollision(i).getGeometry().getName();
            }
            if (hit.equals("Door-geom-1") && dist < 42) {
                bulletAppState.getPhysicsSpace().remove(door_phys);
                door.rotate(0, 1.35f, 0);
                door.move(0, 0, -1.3f);
                attempted = true;
            }
        } else if (name.equals("Click") && attempted == true && !isPressed) {

            for (int i = 0; i < results.size(); i++) {
                // For each hit, we know distance, impact point, name of geometry.
                dist = results.getCollision(i).getDistance();
                hit = results.getCollision(i).getGeometry().getName();
            }
            if (hit.equals("Door-geom-1") && dist < 42) {
                bulletAppState.getPhysicsSpace().add(door_phys);
                door.rotate(0, -1.35f, 0);
                door.move(0, 0, 1.3f);
                attempted = false;
            }


            //Character Controls onAction
        } else if (name.equals(
                "CharLeft")) {
            if (isPressed) {
                left = true;
            } else {
                left = false;
            }
        } else if (name.equals(
                "CharRight")) {
            if (isPressed) {
                right = true;
            } else {
                right = false;
            }
        } else if (name.equals(
                "CharForward")) {
            if (isPressed) {
                up = true;
            } else {
                up = false;
            }
        } else if (name.equals(
                "CharBackward")) {
            if (isPressed) {
                down = true;
            } else {
                down = false;
            }
        } else if (name.equals(
                "CharJump")) {
            character.jump();
        }
    }

    public void onAnimCycleDone(AnimControl control, AnimChannel channel, String animName) {
    }

    public void onAnimChange(AnimControl control, AnimChannel channel, String animName) {
    }

    public void registerInput() {
        inputManager.addMapping("CharLeft", new KeyTrigger(KeyInput.KEY_A));
        inputManager.addMapping("CharRight", new KeyTrigger(KeyInput.KEY_D));
        inputManager.addMapping("CharForward", new KeyTrigger(KeyInput.KEY_W));
        inputManager.addMapping("CharBackward", new KeyTrigger(KeyInput.KEY_S));
        inputManager.addMapping("CharJump", new KeyTrigger(KeyInput.KEY_SPACE));
        inputManager.addMapping("CharAttack", new KeyTrigger(KeyInput.KEY_SPACE));
        inputManager.addMapping("Click", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addListener(this, "Click");
        inputManager.addListener(this, "CharLeft", "CharRight");
        inputManager.addListener(this, "CharForward", "CharBackward");

    }

    public void loadPhysics() {
        bulletAppState = new BulletAppState();
        stateManager.attach(bulletAppState);
    }

    public void loadScene() {
        scene = (Node) assetManager.loadModel("Scenes/Village.j3o");
        scene.setLocalScale(2f);
        scene.setLocalTranslation(0, -20, 0);
        scene.addControl(new RigidBodyControl(0));
        rootNode.attachChild(scene);
        bulletAppState.getPhysicsSpace().addAll(scene);
    }

    public void loadLightSource() {
        AmbientLight light = new AmbientLight();
        light.setColor(ColorRGBA.White.mult(4.5f));
        rootNode.addLight(light);
    }

    public void loadSky() {
        Texture west = assetManager.loadTexture("Textures/Sky/Clouds.jpg");
        Texture east = assetManager.loadTexture("Textures/Sky/Clouds.jpg");
        Texture north = assetManager.loadTexture("Textures/Sky/Clouds.jpg");
        Texture south = assetManager.loadTexture("Textures/Sky/Clouds.jpg");
        Texture up = assetManager.loadTexture("Textures/Sky/Clouds.jpg");
        Texture down = assetManager.loadTexture("Textures/Sky/Clouds.jpg");
        rootNode.attachChild(SkyFactory.createSky(assetManager, west, east, north, south, up, down));
    }

    public void loadCharacter() {
        CapsuleCollisionShape capsule = new CapsuleCollisionShape(2f, 1f);
        character = new CharacterControl(capsule, 0.09f);
        character.setJumpSpeed(20f);
        model = (Node) assetManager.loadModel("Models/Oto/Oto.mesh.xml");
        model.addControl(character);
        model.scale(.5f);
        bulletAppState.getPhysicsSpace().add(character);
        rootNode.attachChild(model);
    }

    public void loadDoor() {
        door = (Node) assetManager.loadModel("Models/Door/Door.mesh.j3o");
        door.setLocalTranslation(-185, -23.2f, 30);
        door.rotate(0, -1.3f, 0);
        door.scale(2f);
        door.setName("Door");
        door_phys = new RigidBodyControl(0);
        door.addControl(door_phys);
        bulletAppState.getPhysicsSpace().add(door_phys);
        clickables.attachChild(door);
    }

    public void loadFish() {
        fish = (Node) assetManager.loadModel("Models/Fish_Green/Fish_Green.mesh.xml");
        fish.setLocalTranslation(0, -50, 0);
        fish.scale(3f);

        path = new MotionPath();
        //first jump (by waterfall)
        path.addWayPoint(new Vector3f(-320, -35, 70));
        path.addWayPoint(new Vector3f(-280, -35, 70));
        path.addWayPoint(new Vector3f(-240, -25, 70));
        path.addWayPoint(new Vector3f(-200, -35, 70));

        //Second jump(middle of river)
        path.addWayPoint(new Vector3f(-40, -35, 50));
        path.addWayPoint(new Vector3f(0, -25, 50));
        path.addWayPoint(new Vector3f(40, -35, 50));

        //Third jump(end of river)
        path.addWayPoint(new Vector3f(200, -35, 75));
        path.addWayPoint(new Vector3f(240, -25, 75));
        path.addWayPoint(new Vector3f(280, -35, 75));
        path.addWayPoint(new Vector3f(320, -35, 75));
        //path.enableDebugShape(assetManager, rootNode);

        motionControl = new MotionEvent(fish, path);

        motionControl.setDirectionType(MotionEvent.Direction.PathAndRotation);
        motionControl.setRotation(new Quaternion().fromAngleNormalAxis(-FastMath.HALF_PI, Vector3f.UNIT_Y));
        motionControl.setInitialDuration(9f);
        motionControl.setSpeed(1f);
        motionControl.setLoopMode(LoopMode.Loop);
        motionControl.play();


        rootNode.attachChild(fish);
    }

    public void loadWaterProcessor() {
        waterProcessor = new SimpleWaterProcessor(assetManager);
        waterProcessor.setReflectionScene(scene);
        waterProcessor.setRenderSize(128, 128);
        waterProcessor.setWaterDepth(.3f);
        waterProcessor.setWaterColor(ColorRGBA.Blue);
    }

    public void loadRiver() {
        Quad riverQuad = new Quad(200, 1100);
        riverQuad.scaleTextureCoordinates(new Vector2f(4f, 4f)); //Wave size
        Geometry riverPlane = new Geometry("RiverPlaneOne", riverQuad);
        riverPlane.setLocalTranslation(520, -28, 140);
        riverPlane.rotate(-1.5707f, 1.6f, 0);
        waterProcessor.setWaterDepth(.2f);
        waterProcessor.setWaterColor(ColorRGBA.Cyan);
        riverPlane.setMaterial(waterProcessor.getMaterial());
        rootNode.attachChild(riverPlane);
    }

    public void loadWaterFall() {
        Quad waterFallQuad = new Quad(90, 121);
        Geometry waterFallPlane = new Geometry("WaterFall", waterFallQuad);
        waterFallPlane.setLocalTranslation(-410, -28, 120);
        waterFallPlane.rotate(-.4f, 1.6f, 0);
        waterFallPlane.setMaterial(waterProcessor.getMaterial());
        rootNode.attachChild(waterFallPlane);
    }

    public void addWaterProcessor() {
        viewPort.addProcessor(waterProcessor);
    }

    public void loadFlyCam() {
        flyCam.setEnabled(false);
        chaseCam = new ChaseCamera(cam, model, inputManager);
        chaseCam.setInvertVerticalAxis(true);
        chaseCam.setDefaultDistance(30);
        chaseCam.setRotationSpeed(2.8f);
    }

    public void startAnimation() {
        animationControl = model.getControl(AnimControl.class);
        animationControl.addListener(
                this);
        animationChannel = animationControl.createChannel();

        animationChannel.setAnim(
                "stand");
    }
}
