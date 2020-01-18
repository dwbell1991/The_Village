
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.AbstractControl;
import com.jme3.scene.control.Control;

public class RotatingControl extends AbstractControl {

    private float speed = 1;
    @Override
    protected void controlUpdate(float tpf) {
        
        spatial.rotate(tpf*speed, 0, 0);
    }

    @Override
    protected void controlRender(RenderManager rm, ViewPort vp) {
    }
    
    public Control cloneForSpatial(Spatial spatial){
        RotatingControl control = new RotatingControl();
        control.setSpatial(spatial);
        control.setSpeed(speed);
        return control;
        
    }
    
    public float getSpeed(){
        return speed;
    }
    
    public void setSpeed(float speed){
        this.speed = speed;
    }
    
}
