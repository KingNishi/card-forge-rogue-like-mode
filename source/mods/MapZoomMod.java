package forge.adventure.util;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.maps.tiled.renderers.BatchTiledMapRenderer;
import forge.adventure.stage.MapStage;
import forge.adventure.stage.PointOfInterestMapRenderer;

/** Match tile culling and map-edge constraints to the actual visible zoomed area. */
public final class MapZoomMod {
    public static void fit(OrthographicCamera camera, float mapWidth, float mapHeight,
                           float focusX, float focusY) {
        if (!(mapWidth > 0 && mapHeight > 0 && camera.viewportWidth > 0 && camera.viewportHeight > 0)) return;
        float fitZoom = Math.min(mapWidth / camera.viewportWidth, mapHeight / camera.viewportHeight);
        camera.zoom = Math.min(camera.zoom, fitZoom);
        float halfWidth = camera.viewportWidth * camera.zoom * 0.5f;
        float halfHeight = camera.viewportHeight * camera.zoom * 0.5f;
        camera.position.x = Math.max(halfWidth, Math.min(focusX, mapWidth - halfWidth));
        camera.position.y = Math.max(halfHeight, Math.min(focusY, mapHeight - halfHeight));
    }

    public static void renderView(OrthographicCamera camera, BatchTiledMapRenderer renderer) {
        camera.update();
        renderer.setView(camera);
    }

    public static void update(Camera camera, MapStage stage, PointOfInterestMapRenderer renderer) {
        OrthographicCamera ortho = (OrthographicCamera) camera;
        fit(ortho, stage.getViewport().getWorldWidth(), stage.getViewport().getWorldHeight(),
            stage.getPlayerSprite().pos().x, stage.getPlayerSprite().pos().y);
        renderView(ortho, renderer);
    }
}
