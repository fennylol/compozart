package compozart.ui;

import compozart.model.Project;

import static compozart.test.Check.*;

public class EditorTest {
    public void testColorPickerReturnsToPreviousTool() {
        Editor ed = new Editor(Project.createDefault());
        ed.setTool(Tool.LINE);
        ed.setTool(Tool.EYEDROPPER);
        ed.pickerDone();
        eq(Tool.LINE, ed.tool());
        ed.pickerDone(); // only acts while the picker is active
        eq(Tool.LINE, ed.tool());
    }

    public void testColorPickerFirstToolFallsBackToDraw() {
        Editor ed = new Editor(Project.createDefault());
        ed.setTool(Tool.EYEDROPPER);
        ed.setTool(Tool.EYEDROPPER);
        ed.pickerDone();
        eq(Tool.DRAW, ed.tool());
    }
}
