//
// Created by xudong wu on 26/02/2017.
// Copyright (c) wuxudong. All rights reserved.
//

import Foundation
import DGCharts
import SwiftyJSON


class RNBarLineChartViewBase: RNYAxisChartViewBase {
    fileprivate var barLineChart: BarLineChartViewBase {
        get {
            return chart as! BarLineChartViewBase
        }
    }

    var savedVisibleRange : NSDictionary?

    var savedZoom : NSDictionary?

    var zoomScaleX: CGFloat?

    var savedExtraOffsets: NSDictionary?

    var _onYaxisMinMaxChange : RCTBubblingEventBlock?
    var timer : Timer?

    override func setYAxis(_ config: NSDictionary) {
        let json = BridgeUtils.toJson(config)

        if json["left"].exists() {
            let leftYAxis = barLineChart.leftAxis
            setCommonAxisConfig(leftYAxis, config: json["left"]);
            setYAxisConfig(leftYAxis, config: json["left"]);
        }


        if json["right"].exists() {
            let rightAxis = barLineChart.rightAxis
            setCommonAxisConfig(rightAxis, config: json["right"]);
            setYAxisConfig(rightAxis, config: json["right"]);
        }
    }

    func setOnYaxisMinMaxChange(_ callback: RCTBubblingEventBlock?) {
      self._onYaxisMinMaxChange = callback;
      self.timer?.invalidate();
      if callback == nil {
        return;
      }

      var lastMin: Double = 0;
      var lastMax: Double = 0;

      let axis = (self.chart as! BarLineChartViewBase).getAxis(.right);

      if #available(iOS 10.0, *) {
        // Interval for 16ms
        self.timer = Timer.scheduledTimer(withTimeInterval: 1/60, repeats: true) { timer in
          let minimum = axis.axisMinimum;
          let maximum = axis.axisMaximum;
          if lastMin != minimum || lastMax != maximum {
            guard let callback = self._onYaxisMinMaxChange else {
              return;
            }
            callback([
              "minY": minimum,
              "maxY": maximum,
            ]);
          }
          lastMin = minimum;
          lastMax = maximum;
        }
      } else {
        // Fallback on earlier versions
      }
    }

    func setMaxHighlightDistance(_  maxHighlightDistance: CGFloat) {
        barLineChart.maxHighlightDistance = maxHighlightDistance;
    }

    func setDrawGridBackground(_  enabled: Bool) {
        barLineChart.drawGridBackgroundEnabled = enabled;
    }


    func setGridBackgroundColor(_ color: Int) {
        barLineChart.gridBackgroundColor = RCTConvert.uiColor(color);
    }


    func setDrawBorders(_ enabled: Bool) {
        barLineChart.drawBordersEnabled = enabled;
    }

    func setBorderColor(_ color: Int) {

        barLineChart.borderColor = RCTConvert.uiColor(color);
    }

    func setBorderWidth(_ width: CGFloat) {
        barLineChart.borderLineWidth = width;
    }


    func setMaxVisibleValueCount(_ count: NSInteger) {
        barLineChart.maxVisibleCount = count;
    }

    func setVisibleRange(_ config: NSDictionary) {
        // delay visibleRange handling until chart data is set
        savedVisibleRange = config
        // execute on next run-loop to ensure layout is finished
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            if self.barLineChart.data != nil {
                self.updateVisibleRange(config)
            }
        }
    }

    func updateVisibleRange(_ config: NSDictionary) {
        let json = BridgeUtils.toJson(config)

        let x = json["x"]
        if x["min"].double != nil {
            let min = x["min"].doubleValue
            barLineChart.setVisibleXRangeMinimum(min)
        }
        if x["max"].double != nil {
            barLineChart.setVisibleXRangeMaximum(x["max"].doubleValue)
        }

        let y = json["y"]
        if y["left"]["min"].double != nil {
            barLineChart.setVisibleYRangeMinimum(y["left"]["min"].doubleValue, axis: YAxis.AxisDependency.left)
        }
        if y["left"]["max"].double != nil {
            barLineChart.setVisibleYRangeMaximum(y["left"]["max"].doubleValue, axis: YAxis.AxisDependency.left)
        }

        if y["right"]["min"].double != nil {
            barLineChart.setVisibleYRangeMinimum(y["right"]["min"].doubleValue, axis: YAxis.AxisDependency.right)
        }
        if y["right"]["max"].double != nil {
            barLineChart.setVisibleYRangeMaximum(y["right"]["max"].doubleValue, axis: YAxis.AxisDependency.right)
        }

        if let target = zoomScaleX, target > 0, barLineChart.scaleX != target {
            let relative = target / barLineChart.scaleX
            let centerX = barLineChart.data?.xMax ?? 0
            let axis = barLineChart.getAxis(.left).isEnabled ? YAxis.AxisDependency.left : YAxis.AxisDependency.right
            barLineChart.zoom(scaleX: relative, scaleY: 1.0, xValue: centerX, yValue: 0.0, axis: axis)
        } else if let saved = savedVisibleRange,
                  let xMap = saved["x"] as? NSDictionary {
            // max가 설정되어 있으면 fitScreen()으로 완전 줌아웃
            // max가 없고 min만 있으면 min 기준 줌인
            let hasMax = xMap["max"] != nil
            if hasMax {
                barLineChart.fitScreen()
            } else if let visibleMin = xMap["min"] as? CGFloat, visibleMin > 0 {
                let rawDataXMin = barLineChart.data?.xMin ?? barLineChart.chartXMin
                let rawDataXMax = barLineChart.data?.xMax ?? barLineChart.chartXMax
                let effectiveXMin = min(rawDataXMin, barLineChart.chartXMin)
                let effectiveXMax = max(rawDataXMax, barLineChart.chartXMax)
                let totalRange = Double(effectiveXMax - effectiveXMin)

                if totalRange > Double(visibleMin) {
                    let relative = totalRange / Double(visibleMin)
                    let centerX: Double = effectiveXMax
                    let axis = barLineChart.getAxis(.left).isEnabled ? YAxis.AxisDependency.left : YAxis.AxisDependency.right
                    barLineChart.zoom(scaleX: relative, scaleY: 1.0, xValue: centerX, yValue: 0.0, axis: axis)
                } else if totalRange > 0 {
                    let relative = Double(visibleMin) / totalRange
                    let centerX: Double = effectiveXMax
                    let axis = barLineChart.getAxis(.left).isEnabled ? YAxis.AxisDependency.left : YAxis.AxisDependency.right
                    barLineChart.zoom(scaleX: CGFloat(relative), scaleY: 1.0, xValue: centerX, yValue: 0.0, axis: axis)
                }
            }
        }

        // Fire after one run-loop so viewPortHandler has updated with new visible range.
        DispatchQueue.main.async { [weak self] in
            self?.sendEvent("visibleRangeChanged")
        }
    }

    func setMaxScale(_ config: NSDictionary) {
        let json = BridgeUtils.toJson(config)

        let maxScaleX = json["x"]
        if maxScaleX.double != nil {
            barLineChart.viewPortHandler.setMaximumScaleX(maxScaleX.doubleValue)
        }

        let maxScaleY = json["y"]
        if maxScaleY.double != nil {
            barLineChart.viewPortHandler.setMaximumScaleY(maxScaleY.doubleValue)
        }
    }

    func setAutoScaleMinMaxEnabled(_  enabled: Bool) {
        barLineChart.autoScaleMinMaxEnabled = enabled
    }

    func setKeepPositionOnRotation(_  enabled: Bool) {
        barLineChart.keepPositionOnRotation = enabled
    }

    func setScaleEnabled(_  enabled: Bool) {
        barLineChart.setScaleEnabled(enabled)
    }

    func setDragEnabled(_  enabled: Bool) {
        barLineChart.dragEnabled = enabled
    }


    func setScaleXEnabled(_  enabled: Bool) {
        barLineChart.scaleXEnabled = enabled
    }

    func setScaleYEnabled(_  enabled: Bool) {
        barLineChart.scaleYEnabled = enabled
    }

    func setPinchZoom(_  enabled: Bool) {
        barLineChart.pinchZoomEnabled = enabled
    }

    func setHighlightPerDragEnabled(_  enabled: Bool) {
        barLineChart.highlightPerDragEnabled = enabled
    }

    func setDoubleTapToZoomEnabled(_  enabled: Bool) {
        barLineChart.doubleTapToZoomEnabled = enabled
    }

    func setZoom(_ config: NSDictionary) {
        self.savedZoom = config
        let json = BridgeUtils.toJson(config)
        if json["scaleX"].float != nil {
            self.zoomScaleX = CGFloat(json["scaleX"].floatValue)
        }
    }

    func updateZoom(_ config: NSDictionary) {
        let json = BridgeUtils.toJson(config)

        if json["scaleX"].float != nil && json["scaleY"].float != nil && json["xValue"].double != nil && json["yValue"].double != nil {
            var axisDependency = YAxis.AxisDependency.left

            if json["axisDependency"].string != nil && json["axisDependency"].stringValue == "RIGHT" {
                axisDependency = YAxis.AxisDependency.right
            }

            barLineChart.zoom(scaleX: CGFloat(json["scaleX"].floatValue),
                    scaleY: CGFloat(json["scaleY"].floatValue),
                    xValue: json["xValue"].doubleValue,
                    yValue: json["yValue"].doubleValue,
                    axis: axisDependency)

            // Dispatch asynchronously to ensure matrix is updated before we read values.
            DispatchQueue.main.async { [weak self] in
                self?.sendEvent("zoomChanged")
            }
        }
    }

    func setViewPortOffsets(_ config: NSDictionary) {
        let json = BridgeUtils.toJson(config)

        var left = json["left"].double != nil ? CGFloat(json["left"].doubleValue) : 0
        if left < 0 { left = 0 }
        let top = json["top"].double != nil ? CGFloat(json["top"].doubleValue) : 0
        let right = json["right"].double != nil ? CGFloat(json["right"].doubleValue) : 0
        let bottom = json["bottom"].double != nil ? CGFloat(json["bottom"].doubleValue) : 0

        barLineChart.setViewPortOffsets(left: left, top: top, right: right, bottom: bottom)
    }

    func setExtraOffsets(_ config: NSDictionary) {
        savedExtraOffsets = config
        applyExtraOffsets()
    }

    func applyExtraOffsets() {
        var left: CGFloat = 0
        var top: CGFloat = 0
        var right: CGFloat = 0
        var bottom: CGFloat = 0
        if let config = savedExtraOffsets {
            let json = BridgeUtils.toJson(config)
            left = json["left"].double != nil ? CGFloat(json["left"].doubleValue) : 0
            if left < 0 { left = 0 }
            top = json["top"].double != nil ? CGFloat(json["top"].doubleValue) : 0
            right = json["right"].double != nil ? CGFloat(json["right"].doubleValue) : 0
            bottom = json["bottom"].double != nil ? CGFloat(json["bottom"].doubleValue) : 0
        }
        let beforeBottomOffset = barLineChart.viewPortHandler.offsetBottom
        if edgeLabelEnabled {
            var axisHeight = barLineChart.xAxis.labelFont.lineHeight / 2
            if xAxisContainsNewline() {
                axisHeight = barLineChart.xAxis.labelFont.lineHeight
            }
            bottom += axisHeight + edgeLabelHeight() / 2
        }
        
        barLineChart.setExtraOffsets(left: left, top: top, right: right, bottom: bottom)
        barLineChart.notifyDataSetChanged()
    }

    private func xAxisContainsNewline() -> Bool {
        let axis = barLineChart.xAxis
        guard let formatter = axis.valueFormatter else { return false }
        let maxIdx = Int(axis.axisMaximum)
        for i in 0..<maxIdx {
            if formatter.stringForValue(Double(i), axis: axis).contains("\n") {
                return true
            }
        }
        return false
    }

    override func onAfterDataSetChanged() {
        super.onAfterDataSetChanged()

        applyExtraOffsets()

        // clear zoom after applied, but keep visibleRange
        if let visibleRange = savedVisibleRange {
            updateVisibleRange(visibleRange)

            // Auto zoom to the minimum visibleRange (parity with Android implementation)
            // if savedZoom == nil {
            //     if let x = visibleRange["x"] as? NSDictionary,
            //        let min = x["min"] as? CGFloat,
            //        min > 0 {
            //         let currentRange = barLineChart.visibleXRange
            //         if currentRange > Double(min) {
            //             let relativeScale = currentRange / Double(min)
            //             let centerX = barLineChart.chartXMax
            //             let axis: YAxis.AxisDependency = barLineChart.leftAxis.enabled ? .left : .right
            //             barLineChart.zoom(scaleX: CGFloat(relativeScale),
            //                               scaleY: 1.0,
            //                               xValue: centerX,
            //                               yValue: 0.0,
            //                               axis: axis)
            //         }
            //     }
            // }
        }

        if let zoom = savedZoom {
            updateZoom(zoom)
            savedZoom = nil
        }
    }

    func setDataAndLockIndex(_ data: NSDictionary) {
        let json = BridgeUtils.toJson(data)

        let axis = barLineChart.getAxis(YAxis.AxisDependency.left).enabled ? YAxis.AxisDependency.left : YAxis.AxisDependency.right

        let contentRect = barLineChart.contentRect

        let originCenterValue = barLineChart.valueForTouchPoint(point: CGPoint(x: contentRect.midX, y: contentRect.midY), axis: axis)

        let originalVisibleXRange = barLineChart.visibleXRange
        let originalVisibleYRange = getVisibleYRange(axis)

        barLineChart.fitScreen()

        barLineChart.data = dataExtract.extract(json)
        barLineChart.notifyDataSetChanged()


        let newVisibleXRange = barLineChart.visibleXRange
        let newVisibleYRange = getVisibleYRange(axis)

        let scaleX = newVisibleXRange / originalVisibleXRange
        let scaleY = newVisibleYRange / originalVisibleYRange

        // in iOS Charts chart.zoom scaleX: CGFloat, scaleY: CGFloat, xValue: Double, yValue: Double, axis: YAxis.AxisDependency)
        // the scale is absolute scale, it will overwrite touchMatrix scale directly
        // but in android MpAndroidChart, ZoomJob getInstance(viewPortHandler, scaleX, scaleY, xValue, yValue, trans, axis, v)
        // the scale is relative scale, touchMatrix.scaleX = touchMatrix.scaleX * scaleX
        // so in iOS, we updateVisibleRange after zoom

        barLineChart.zoom(scaleX: CGFloat(scaleX), scaleY: CGFloat(scaleY), xValue: Double(originCenterValue.x), yValue: Double(originCenterValue.y), axis: axis)

        if let config = savedVisibleRange {
            updateVisibleRange(config)
        }
        barLineChart.notifyDataSetChanged()

        sendEvent("chartLoadComplete")
    }

    func getVisibleYRange(_ axis: YAxis.AxisDependency) -> CGFloat {
        let contentRect = barLineChart.contentRect

        return barLineChart.valueForTouchPoint(point: CGPoint(x: contentRect.maxX, y:contentRect.minY), axis: axis).y - barLineChart.valueForTouchPoint(point: CGPoint(x: contentRect.minX, y:contentRect.maxY), axis: axis).y
    }

    // func setLandscapeOrientation(_ enabled: Bool) {
        // Currently unused — layout for landscape mode is handled in JS.
        // Adding this setter prevents unknown-prop warnings on iOS.
    // }
}
