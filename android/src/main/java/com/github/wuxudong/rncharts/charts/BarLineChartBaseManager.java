package com.github.wuxudong.rncharts.charts;

import android.graphics.RectF;

import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.github.mikephil.charting.charts.BarLineChartBase;
import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.jobs.ZoomJob;
import com.github.mikephil.charting.listener.BarLineChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.github.mikephil.charting.utils.MPPointD;
import com.github.mikephil.charting.utils.MPPointF;
import com.github.mikephil.charting.utils.Transformer;
import com.github.wuxudong.rncharts.listener.RNOnChartGestureListener;
import com.github.wuxudong.rncharts.utils.BridgeUtils;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

class ChartExtraProperties {
    public ReadableMap savedVisibleRange = null;
    public String group = null;
    public String identifier = null;
    public boolean syncX = true;
    public boolean syncY = false;
    // when true, apply one-time auto zoom based on savedVisibleRange after data update
    public boolean autoZoomPending = false;
    public Float zoomScaleX = null;
}

class ExtraPropertiesHolder {


    private WeakHashMap<BarLineChartBase, ChartExtraProperties> extraProperties = new WeakHashMap<>();

    public synchronized ChartExtraProperties getExtraProperties(BarLineChartBase chart) {
        if (!extraProperties.containsKey(chart)) {
            extraProperties.put(chart, new ChartExtraProperties());
        }

        return extraProperties.get(chart);
    }
}

public abstract class BarLineChartBaseManager<T extends BarLineChartBase, U extends Entry> extends YAxisChartBase<T, U> {

    private ExtraPropertiesHolder extraPropertiesHolder = new ExtraPropertiesHolder();

    @Override
    public void setYAxis(Chart chart, ReadableMap propMap) {
        BarLineChartBase barLineChart = (BarLineChartBase) chart;

        if (BridgeUtils.validate(propMap, ReadableType.Map, "left")) {
            YAxis leftYAxis = barLineChart.getAxisLeft();
            setCommonAxisConfig(chart, leftYAxis, propMap.getMap("left"));
            setYAxisConfig(leftYAxis, propMap.getMap("left"));
        }
        if (BridgeUtils.validate(propMap, ReadableType.Map, "right")) {
            YAxis rightYAxis = barLineChart.getAxisRight();
            setCommonAxisConfig(chart, rightYAxis, propMap.getMap("right"));
            setYAxisConfig(rightYAxis, propMap.getMap("right"));
        }
    }

    @ReactProp(name = "maxHighlightDistance")
    public void setMaxHighlightDistance(BarLineChartBase chart, float maxHighlightDistance) {
        chart.setMaxHighlightDistance(maxHighlightDistance);
    }

    @ReactProp(name = "drawGridBackground")
    public void setDrawGridBackground(BarLineChartBase chart, boolean enabled) {
        chart.setDrawGridBackground(enabled);
    }

    @ReactProp(name = "gridBackgroundColor")
    public void setGridBackgroundColor(BarLineChartBase chart, Integer color) {
        chart.setGridBackgroundColor(color);
    }

    @ReactProp(name = "drawBorders")
    public void setDrawBorders(BarLineChartBase chart, boolean enabled) {
        chart.setDrawBorders(enabled);
    }

    @ReactProp(name = "borderColor")
    public void setBorderColor(BarLineChartBase chart, Integer color) {
        chart.setBorderColor(color);
    }

    @ReactProp(name = "borderWidth")
    public void setBorderWidth(BarLineChartBase chart, float width) {
        chart.setBorderWidth(width);
    }

    @ReactProp(name = "maxVisibleValueCount")
    public void setMaxVisibleValueCount(BarLineChartBase chart, int count) {
        chart.setMaxVisibleValueCount(count);
    }

    @ReactProp(name = "visibleRange")
    public void setVisibleXRangeMinimum(BarLineChartBase chart, ReadableMap propMap) {
         // delay visibleRange handling until chart data is set
         ChartExtraProperties props = extraPropertiesHolder.getExtraProperties(chart);
         props.savedVisibleRange = propMap;
         props.autoZoomPending = true;

    }

    private void updateVisibleRange(BarLineChartBase chart, ReadableMap propMap) {
        if (BridgeUtils.validate(propMap, ReadableType.Map, "x")) {
            ReadableMap x = propMap.getMap("x");
            if (BridgeUtils.validate(x, ReadableType.Number, "min")) {
                chart.setVisibleXRangeMinimum((float) x.getDouble("min"));
            }

            if (BridgeUtils.validate(x, ReadableType.Number, "max")) {
                chart.setVisibleXRangeMaximum((float) x.getDouble("max"));
            }
        }

        if (BridgeUtils.validate(propMap, ReadableType.Map, "y")) {
            ReadableMap y = propMap.getMap("y");

            if (BridgeUtils.validate(y, ReadableType.Map, "left")) {
                ReadableMap left = y.getMap("left");
                if (BridgeUtils.validate(left, ReadableType.Number, "min")) {
                    chart.setVisibleYRangeMinimum((float) left.getDouble("min"), YAxis.AxisDependency.LEFT);
                }

                if (BridgeUtils.validate(left, ReadableType.Number, "max")) {
                    chart.setVisibleYRangeMaximum((float) left.getDouble("max"), YAxis.AxisDependency.LEFT);
                }
            }

            if (BridgeUtils.validate(y, ReadableType.Map, "right")) {
                ReadableMap right = y.getMap("right");
                if (BridgeUtils.validate(right, ReadableType.Number, "min")) {
                    chart.setVisibleYRangeMinimum((float) right.getDouble("min"), YAxis.AxisDependency.RIGHT);
                }

                if (BridgeUtils.validate(right, ReadableType.Number, "max")) {
                    chart.setVisibleYRangeMaximum((float) right.getDouble("max"), YAxis.AxisDependency.RIGHT);
                }
            }
        }

        ChartExtraProperties extra = extraPropertiesHolder.getExtraProperties(chart);

        if (extra.zoomScaleX != null) {
            float targetScale = extra.zoomScaleX;
            if (targetScale > 0 && chart.getScaleX() != targetScale) {
                float relativeScale = targetScale / chart.getScaleX();
                float centerX = chart.getData() != null ? (float) chart.getData().getXMax() : 0f;
                YAxis.AxisDependency axis = chart.getAxisLeft().isEnabled() ?
                        YAxis.AxisDependency.LEFT : YAxis.AxisDependency.RIGHT;
                chart.zoom(relativeScale, 1f, centerX, 0f, axis);
                // clear initial zoom so that future manual zooms aren't overridden
                extra.zoomScaleX = null;
            }
        } else {
             if (extra.autoZoomPending) {
                // Remove any existing edge labels before recalculating based on new data.
                com.github.wuxudong.rncharts.charts.helpers.EdgeLabelHelper.setEnabled(chart, false);
                ReadableMap saved = extra.savedVisibleRange;
                if (saved != null && BridgeUtils.validate(saved, ReadableType.Map, "x")) {
                    ReadableMap xRange = saved.getMap("x");
                    float rawDataXMin = chart.getData() != null ? (float) chart.getData().getXMin() : chart.getXChartMin();
                    float rawDataXMax = chart.getData() != null ? (float) chart.getData().getXMax() : chart.getXChartMax();
                    float effectiveXMin = Math.min(rawDataXMin, chart.getXChartMin());
                    float effectiveXMax = Math.max(rawDataXMax, chart.getXChartMax());
                    float totalRange = effectiveXMax - effectiveXMin;

                    // max가 설정되어 있으면 fitScreen()으로 완전 줌아웃 (전체 데이터 표시)
                    // max가 없고 min만 있으면 기존 로직대로 min 기준 줌인
                    boolean hasMax = BridgeUtils.validate(xRange, ReadableType.Number, "max");
                    boolean hasMin = BridgeUtils.validate(xRange, ReadableType.Number, "min");

                    if (hasMax) {
                        // max 설정 시: fitScreen으로 줌 초기화 → 전체 데이터 표시
                        chart.fitScreen();
                    } else if (hasMin) {
                        float visibleMin = (float) xRange.getDouble("min");
                        if (visibleMin > 0 && totalRange > 0) {
                            float relativeScale;
                            if (totalRange > visibleMin) {
                                relativeScale = totalRange / visibleMin;
                            } else {
                                relativeScale = visibleMin / totalRange;
                            }
                            float centerX = effectiveXMax;
                            YAxis.AxisDependency axis = chart.getAxisLeft().isEnabled()
                                    ? YAxis.AxisDependency.LEFT : YAxis.AxisDependency.RIGHT;
                            chart.zoom(relativeScale, 1f, centerX, 0f, axis);
                        }
                    }
                    // auto zoom handled
                    extra.autoZoomPending = false;
                }
            }
        }
    }

    @ReactProp(name = "autoScaleMinMaxEnabled")
    public void setAutoScaleMinMaxEnabled(BarLineChartBase chart, boolean enabled) {
        chart.setAutoScaleMinMaxEnabled(enabled);
    }

    @ReactProp(name = "keepPositionOnRotation")
    public void setKeepPositionOnRotation(BarLineChartBase chart, boolean enabled) {
        chart.setKeepPositionOnRotation(enabled);
    }

    @ReactProp(name = "scaleEnabled")
    public void setScaleEnabled(BarLineChartBase chart, boolean enabled) {
        chart.setScaleEnabled(enabled);
    }

    @ReactProp(name = "dragEnabled")
    public void setDragEnabled(BarLineChartBase chart, boolean enabled) {
        chart.setDragEnabled(enabled);
    }

    @ReactProp(name = "highlightPerDragEnabled")
    public void setHighlightPerDragEnabled(BarLineChartBase chart, boolean enabled) {
        chart.setHighlightPerDragEnabled(enabled);
    }

    @ReactProp(name = "scaleXEnabled")
    public void setScaleXEnabled(BarLineChartBase chart, boolean enabled) {
        chart.setScaleXEnabled(enabled);
    }

    @ReactProp(name = "scaleYEnabled")
    public void setScaleYEnabled(BarLineChartBase chart, boolean enabled) {
        chart.setScaleYEnabled(enabled);
    }

    @ReactProp(name = "pinchZoom")
    public void setPinchZoom(BarLineChartBase chart, boolean enabled) {
        chart.setPinchZoom(enabled);
    }

    @ReactProp(name = "doubleTapToZoomEnabled")
    public void setDoubleTapToZoomEnabled(BarLineChartBase chart, boolean enabled) {
        chart.setDoubleTapToZoomEnabled(enabled);
    }

    @ReactProp(name = "zoom")
    public void setZoom(BarLineChartBase chart, ReadableMap propMap) {
        if (BridgeUtils.validate(propMap, ReadableType.Number, "scaleX") &&
                BridgeUtils.validate(propMap, ReadableType.Number, "scaleY") &&
                BridgeUtils.validate(propMap, ReadableType.Number, "xValue") &&
                BridgeUtils.validate(propMap, ReadableType.Number, "yValue")) {

            YAxis.AxisDependency axisDependency = YAxis.AxisDependency.LEFT;
            if (propMap.hasKey("axisDependency") &&
                    propMap.getString("axisDependency").equalsIgnoreCase("RIGHT")) {
                axisDependency = YAxis.AxisDependency.RIGHT;
            }

            chart.zoom(
                    (float) propMap.getDouble("scaleX") / chart.getScaleX(),
                    (float) propMap.getDouble("scaleY") / chart.getScaleY(),
                    (float) propMap.getDouble("xValue"),
                    (float) propMap.getDouble("yValue"),
                    axisDependency
            );

            // sendLoadCompleteEvent(chart);
            extraPropertiesHolder.getExtraProperties(chart).zoomScaleX = (float) propMap.getDouble("scaleX");
        }
    }

    @ReactProp(name = "landscapeOrientation")
    public void setLandscapeOrientation(BarLineChartBase chart, boolean enabled) {
        // Forward the override flag to EdgeLabelHelper so that value-label/edge-label logic
        // respects the orientation provided by JS.
        com.github.wuxudong.rncharts.charts.helpers.EdgeLabelHelper.setLandscapeOverride(chart, enabled);
        com.github.wuxudong.rncharts.charts.helpers.EdgeLabelHelper.applyPadding(chart);
        com.github.wuxudong.rncharts.charts.helpers.EdgeLabelHelper.update(chart, chart.getLowestVisibleX(), chart.getHighestVisibleX());
    }

    @ReactProp(name = "eventThrottle", defaultInt = 100)
    public void setEventThrottle(BarLineChartBase chart, int throttleMs) {
        OnChartGestureListener listener = chart.getOnChartGestureListener();
        if (listener instanceof RNOnChartGestureListener) {
            ((RNOnChartGestureListener) listener).setEventThrottle(throttleMs);
        }
    }

    // Note: Offset aren't updated until first touch event: https://github.com/PhilJay/MPAndroidChart/issues/892
    @ReactProp(name = "viewPortOffsets")
    public void setViewPortOffsets(BarLineChartBase chart, ReadableMap propMap) {
        double left = 0, top = 0, right = 0, bottom = 0;

        if (BridgeUtils.validate(propMap, ReadableType.Number, "left")) {
            left = propMap.getDouble("left");
        }
        if (BridgeUtils.validate(propMap, ReadableType.Number, "top")) {
            top = propMap.getDouble("top");
        }
        if (BridgeUtils.validate(propMap, ReadableType.Number, "right")) {
            right = propMap.getDouble("right");
        }
        if (BridgeUtils.validate(propMap, ReadableType.Number, "bottom")) {
            bottom = propMap.getDouble("bottom");
        }
        chart.setViewPortOffsets((float) left, (float) top, (float) right, (float) bottom);
    }

    @ReactProp(name = "extraOffsets")
    public void setExtraOffsets(BarLineChartBase chart, ReadableMap propMap) {
        double left = 0, top = 0, right = 0, bottom = 0;

        if (BridgeUtils.validate(propMap, ReadableType.Number, "left")) {
            left = propMap.getDouble("left");
        }
        if (BridgeUtils.validate(propMap, ReadableType.Number, "top")) {
            top = propMap.getDouble("top");
        }
        if (BridgeUtils.validate(propMap, ReadableType.Number, "right")) {
            right = propMap.getDouble("right");
        }
        if (BridgeUtils.validate(propMap, ReadableType.Number, "bottom")) {
            bottom = propMap.getDouble("bottom");
        }
        com.github.wuxudong.rncharts.charts.helpers.EdgeLabelHelper.saveBaseOffsets(chart, (float) left, (float) top, (float) right, (float) bottom);
        com.github.wuxudong.rncharts.charts.helpers.EdgeLabelHelper.applyPadding(chart);
    }

    @ReactProp(name = "group")
    public void setGroup(BarLineChartBase chart, String group) {
        extraPropertiesHolder.getExtraProperties(chart).group = group;
    }

    @ReactProp(name = "identifier")
    public void setIdentifier(BarLineChartBase chart, String identifier) {
        extraPropertiesHolder.getExtraProperties(chart).identifier = identifier;
    }

    @ReactProp(name = "syncX")
    public void setSyncX(BarLineChartBase chart, boolean syncX) {
        extraPropertiesHolder.getExtraProperties(chart).syncX = syncX;
    }

    @ReactProp(name = "syncY")
    public void setSyncY(BarLineChartBase chart, boolean syncY) {
        extraPropertiesHolder.getExtraProperties(chart).syncY = syncY;
    }

    @Nullable
    @Override
    public Map<String, Integer> getCommandsMap() {
        Map<String, Integer> commandsMap = super.getCommandsMap();

        Map<String, Integer> map = MapBuilder.of(
                "moveViewTo", MOVE_VIEW_TO,
                "moveViewToX", MOVE_VIEW_TO_X,
                "moveViewToAnimated", MOVE_VIEW_TO_ANIMATED,
                "fitScreen", FIT_SCREEN,
                "highlights", HIGHLIGHTS,
                "setDataAndLockIndex", SET_DATA_AND_LOCK_INDEX);

        if (commandsMap != null) {
            map.putAll(commandsMap);
        }
        return map;
    }

    @Override
    public void receiveCommand(T root, int commandId, @Nullable ReadableArray args) {
        switch (commandId) {
            case MOVE_VIEW_TO:
                root.moveViewTo((float) args.getDouble(0), (float) args.getDouble(1), args.getString(2).equalsIgnoreCase("right") ? YAxis.AxisDependency.RIGHT : YAxis.AxisDependency.LEFT);
                return;

            case MOVE_VIEW_TO_X:
                root.moveViewToX((float) args.getDouble(0));
                return;

            case MOVE_VIEW_TO_ANIMATED:
                root.moveViewToAnimated((float) args.getDouble(0), (float) args.getDouble(1), args.getString(2).equalsIgnoreCase("right") ? YAxis.AxisDependency.RIGHT : YAxis.AxisDependency.LEFT, args.getInt(3));
                return;

            case CENTER_VIEW_TO:
                root.centerViewTo((float) args.getDouble(0), (float) args.getDouble(1), args.getString(2).equalsIgnoreCase("right") ? YAxis.AxisDependency.RIGHT : YAxis.AxisDependency.LEFT);
                return;

            case CENTER_VIEW_TO_ANIMATED:
                root.centerViewToAnimated((float) args.getDouble(0), (float) args.getDouble(1), args.getString(2).equalsIgnoreCase("right") ? YAxis.AxisDependency.RIGHT : YAxis.AxisDependency.LEFT, args.getInt(3));
                return;

            case FIT_SCREEN:
                root.fitScreen();
                return;

            case HIGHLIGHTS:
                this.setHighlights(root, args.getArray(0));
                return;

            case SET_DATA_AND_LOCK_INDEX:
                setDataAndLockIndex(root, args.getMap(0));
                return;
        }

        super.receiveCommand(root, commandId, args);
    }

    private void setDataAndLockIndex(T root, ReadableMap map) {
        YAxis.AxisDependency axisDependency = root.getAxisLeft().isEnabled() ? YAxis.AxisDependency.LEFT : YAxis.AxisDependency.RIGHT;
        Transformer transformer = root.getTransformer(axisDependency);

        RectF contentRect = root.getViewPortHandler().getContentRect();
        float originalCenterXPixel = contentRect.centerX();
        float originalCenterYPixel = contentRect.centerY();

        MPPointD originalCenterValue = root.getValuesByTouchPoint(originalCenterXPixel, originalCenterYPixel, axisDependency);
        double originalVisibleXRange = root.getVisibleXRange();
        double originalVisibleYRange = getVisibleYRange(root, axisDependency);

        root.setData((getDataExtract().extract(root, map)));
        ReadableMap savedVisibleRange = extraPropertiesHolder.getExtraProperties(root).savedVisibleRange;
        if (savedVisibleRange != null) {
            updateVisibleRange(root, savedVisibleRange);
        }

        MPPointD newPixelForOriginalCenter = transformer.getPixelForValues((float) originalCenterValue.x, (float) originalCenterValue.y);

        // it only work when no scale involved, otherwise xBias and yBias is not accurate
        double xBias = newPixelForOriginalCenter.x - originalCenterXPixel;
        double yBias = newPixelForOriginalCenter.y - originalCenterYPixel;

        double newVisibleXRange = root.getVisibleXRange();
        double newVisibleYRange = getVisibleYRange(root, axisDependency);

        double scaleX = (newVisibleXRange / originalVisibleXRange);
        double scaleY = (newVisibleYRange / originalVisibleYRange);

        ZoomJob.getInstance(root.getViewPortHandler(), (float) scaleX, (float) scaleY, (float) originalCenterValue.x, (float) originalCenterValue.y, transformer, axisDependency, root).run();

        try {
            // if there is a dragging action in process, because BarLineChartTouchListener hold an another copy of matrixTouch when touch start, add extra offset to it.
            // unfortunately, mTouchStartPoint is private
            Field fieldTouchStartPoint = BarLineChartTouchListener.class.getDeclaredField("mTouchStartPoint");
            fieldTouchStartPoint.setAccessible(true);
            MPPointF mTouchStartPoint = (MPPointF) fieldTouchStartPoint.get(root.getOnTouchListener());

            mTouchStartPoint.x += xBias;
            mTouchStartPoint.y += yBias;
        } catch (Exception e) {
            e.printStackTrace();
        }

        sendLoadCompleteEvent(root);

    }

    private double getVisibleYRange(T chart, YAxis.AxisDependency axisDependency) {
        RectF contentRect = chart.getViewPortHandler().getContentRect();

        return chart.getValuesByTouchPoint(contentRect.right, contentRect.top, axisDependency).y -
                chart.getValuesByTouchPoint(contentRect.left, contentRect.bottom, axisDependency).y;

    }

    @Override
    protected void onAfterDataSetChanged(T chart) {
        super.onAfterDataSetChanged(chart);

        ChartExtraProperties extraProperties = extraPropertiesHolder.getExtraProperties(chart);

        if (extraProperties.savedVisibleRange != null) {
            extraProperties.autoZoomPending = true;
            updateVisibleRange(chart, extraProperties.savedVisibleRange);
        }


        if (extraProperties.group != null && extraProperties.identifier != null) {
            OnChartGestureListener onChartGestureListener = chart.getOnChartGestureListener();

            if (onChartGestureListener != null && onChartGestureListener instanceof RNOnChartGestureListener) {
                RNOnChartGestureListener rnOnChartGestureListener = (RNOnChartGestureListener) onChartGestureListener;
                rnOnChartGestureListener.setGroup(extraProperties.group);
                rnOnChartGestureListener.setIdentifier(extraProperties.identifier);
            }

            ChartGroupHolder.addChart(extraProperties.group, extraProperties.identifier, chart, extraProperties.syncX, extraProperties.syncY);
        }

        // Keep axis limits as provided by JS or autoscale; no implicit tweaks here.
    }
}
