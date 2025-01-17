import { Component, Ref, Watch } from 'vue-property-decorator';
import Highcharts from 'highcharts';
import { ChartOption, ChartOptionData, ScatterChartOption, ScatterQuerySetting, TextSetting } from '@core/common/domain/model';
import { merge } from 'lodash';
import { BaseHighChartWidget, PropsBaseChart } from '@chart/BaseChart.ts';
import { ClassProfiler } from '@/shared/profiler/Annotation';
import { DIException } from '@core/common/domain/exception';
import { SeriesTwoResponse } from '@core/common/domain/response';
import { HighchartUtils, MetricNumberMode } from '@/utils';
import { RenderController } from '@chart/custom/RenderController';
import { Di } from '@core/common/modules';
import { PageRenderService } from '@chart/custom/PageRenderService';
import { RenderProcessService } from '@chart/custom/RenderProcessService';
import { NumberFormatter, RangeData } from '@core/common/services/Formatter';
import { Log } from '@core/utils';

@Component({
  props: PropsBaseChart
})
@ClassProfiler({ prefix: 'ScatterChart' })
export default class ScatterChart extends BaseHighChartWidget<SeriesTwoResponse, ScatterChartOption, ScatterQuerySetting> {
  @Ref()
  chart: any;

  // todo: fixme return correct default type
  protected renderController: RenderController<SeriesTwoResponse>;
  private numberFormatter!: NumberFormatter;

  constructor() {
    super();
    const tooltipFormatter = this.tooltipFormatter;
    const xAxisFormatter = this.xAxisFormatter;
    const yAxisFormatter = this.yAxisFormatter;
    const manualOptions: Highcharts.Options = {
      chart: {
        type: 'scatter',
        zoomType: 'xy'
      },
      colors: this.setting.colors,
      plotOptions: {
        scatter: this.createRightClickAsOptions()
      },
      subtitle: {
        useHTML: true
      },
      xAxis: [
        {
          labels: {
            useHTML: true,
            formatter: function() {
              return xAxisFormatter((this as any) as Highcharts.AxisLabelsFormatterContextObject<any>);
            }
          }
        },
        {
          labels: {
            useHTML: true,
            formatter: function() {
              return xAxisFormatter((this as any) as Highcharts.AxisLabelsFormatterContextObject<any>);
            }
          }
        }
      ],
      yAxis: [
        {
          labels: {
            useHTML: true,
            formatter: function() {
              return yAxisFormatter((this as any) as Highcharts.AxisLabelsFormatterContextObject<any>);
            }
          }
        },
        {
          labels: {
            useHTML: true,
            formatter: function() {
              return yAxisFormatter((this as any) as Highcharts.AxisLabelsFormatterContextObject<any>);
            }
          }
        }
      ],

      tooltip: {
        useHTML: true,
        // Ở Data builder:
        // Nếu để outside = true, thì sẽ không hiện tooltip.
        // Nếu để outside = false, thì không hiện được tooltip nằm trên cùng của chart do dataLabel và tooltip đều dùng use HTML = true, dẫn đến lỗi khi ở dashboard
        // Giải pháp tạm thời: Hardcode outside = false nếu ở Data builder; = true nếu ở dashboard/các trường hợp khác
        outside: !this.isPreview,
        formatter: function(tooltip) {
          return tooltipFormatter((this as any) as Highcharts.TooltipFormatterContextObject);
        }
      }
    };
    this.updateOptions(manualOptions);
    this.numberFormatter = this.buildFormatterByMetricNumber(
      this.setting.options.metricNumbers ?? MetricNumberMode.Default,
      this.setting.options.precision ?? 2
    );
    this.renderController = this.createRenderController();
  }

  @Watch('setting')
  onChartSettingChanged() {
    this.reRenderChart();
  }

  @Watch('data')
  onChartDataChanged() {
    this.reRenderChart();
  }

  @Watch('textColor')
  onTextColorChanged() {
    if (this.isCustomDisplay()) {
      this.buildCustomChart();
    } else {
      this.updateTextColor(this.textColor, true);
    }
  }

  @Watch('setting.options.metricNumbers')
  onNumberMetricChanged(newMetricNumberMode: MetricNumberMode) {
    const newMetricNumber: string[] | undefined = HighchartUtils.toMetricNumbers(newMetricNumberMode);
    const newRanges: RangeData[] | undefined = HighchartUtils.buildRangeData(newMetricNumber);
    this.numberFormatter.setRanges(newRanges);
  }

  @Watch('setting.options.precision')
  onPrecisionChanged(precision: number) {
    this.numberFormatter.precision = precision;
  }

  mounted() {
    this.reRenderChart();
  }

  beforeDestroy() {
    this.renderController.dispose();
  }

  updateOptions(newOptions: any) {
    this.options = merge({}, ChartOption.CONFIG, ScatterChartOption.DEFAULT_SETTING, this.options, newOptions);
  }

  isHorizontalZoomIn(): boolean {
    return false;
  }

  isHorizontalZoomOut(): boolean {
    return false;
  }

  getChart(): Highcharts.Chart | undefined {
    return this.chart.getChart();
  }

  protected buildHighchart() {
    try {
      this.updateMetricNumber(this.setting.options);
      HighchartUtils.reset(this.getChart());
      this.load(this.data);
      this.buildAxis(this.data);
      HighchartUtils.updateChart(this.getChart(), this.setting.options);
      this.updateChartInfo();
      HighchartUtils.drawChart(this.getChart());
      this.assignDrilldownClick();
    } catch (e) {
      if (e instanceof DIException) {
        throw e;
      } else {
        Log.error(`HighchartsScatterChart:: buildChart:: ${e}`);
        throw new DIException('Error when display chart. Please try again!');
      }
    }
  }

  protected load(chartData: SeriesTwoResponse) {
    HighchartUtils.addSeries(this.getChart(), chartData.series);
  }

  protected buildAxis(chartData: SeriesTwoResponse) {
    const options: any = {};
    if (chartData.xAxis) {
      options['xAxis'] = {
        type: 'category',
        categories: chartData.xAxis
      };
    }
    if (chartData.yAxis) {
      options['yAxis'] = {
        type: 'category',
        categories: chartData.yAxis
      };
    }
    HighchartUtils.updateChart(this.getChart(), options);
  }

  protected resizeHighchart(): void {
    this.getChart()?.reflow();
  }

  private createRenderController(): RenderController<SeriesTwoResponse> {
    const pageRenderService = Di.get(PageRenderService);
    const processRenderService = Di.get(RenderProcessService);
    return new RenderController(pageRenderService, processRenderService);
  }

  private updateTextColor(color: string | undefined, reDraw: boolean): void {
    // if (color) {
    //   const newStyle = {
    //     color: color
    //   };
    //   const newColorOption = {
    //     legend: {
    //       itemStyle: newStyle
    //     },
    //     xAxis: [
    //       {
    //         title: {
    //           style: newStyle
    //         },
    //         labels: {
    //           style: newStyle
    //         }
    //       }
    //     ],
    //     yAxis: [
    //       {
    //         title: {
    //           style: newStyle
    //         },
    //         labels: {
    //           style: newStyle
    //         }
    //       }
    //     ]
    //   };
    //   HighchartUtils.updateChart(this.getChart(), newColorOption, reDraw);
    // }
  }

  private updateMetricNumber(options: ChartOptionData) {
    const metrixNumber: string[] | undefined = HighchartUtils.toMetricNumbers(options.metricNumbers ?? MetricNumberMode.Default);
    Highcharts.setOptions({
      lang: {
        numericSymbols: metrixNumber
      }
    });
  }

  private buildFormatterByMetricNumber(metricNumber: MetricNumberMode, precision: number) {
    const metricNumbers = HighchartUtils.toMetricNumbers(metricNumber);
    const ranges: RangeData[] | undefined = HighchartUtils.buildRangeData(metricNumbers);
    return new NumberFormatter(ranges, precision);
  }

  private tooltipFormatter(point: Highcharts.TooltipFormatterContextObject): string {
    // Log.debug('Scatter::tooltip::', point);
    const formattedDataXAxis = this.numberFormatter.formatWithType(point.x, this.query.xAxis.function.field.fieldType);
    const formattedDataYAxis = this.numberFormatter.formatWithType(point.y, this.query.yAxis.function.field.fieldType);
    const xAxisLabel = this.query.xAxis.name;
    const yAxisLabel = this.query.yAxis.name;
    const legend = point.series.name;
    const color = point.color;
    const textColor = this.setting?.options?.tooltip?.style?.color ?? '#fff';
    const fontFamily = this.setting?.options?.tooltip?.style?.fontFamily ?? 'Roboto';
    return `<div style="color: ${textColor}; font-family: ${fontFamily}; text-align: left;">
              <div><span style="color:${color}; padding-right: 5px;">●</span>${legend}</div>
              <span>${xAxisLabel}: <b>${formattedDataXAxis}</b></span></br>
              <span>${yAxisLabel}: <b>${formattedDataYAxis}</b></span>
            </div>`;
  }
  private yAxisFormatter(axis: Highcharts.AxisLabelsFormatterContextObject<any>) {
    const yAxisSetting = this.setting.options.yAxis;
    const value = this.numberFormatter.format(axis.value);
    if (yAxisSetting && yAxisSetting[0]) {
      return this.customAxisLabel(value, yAxisSetting[0].prefix, yAxisSetting[0].postfix);
    } else {
      return `<div>${value}</div>`;
    }
  }

  private customAxisLabel(value: string, prefix?: TextSetting, postfix?: TextSetting) {
    return `
        <div class="d-flex align-items-center">
          <div>${prefix?.text ?? ''}</div>${value}<div>${postfix?.text ?? ''}</div>
        </div>
      `;
  }

  private xAxisFormatter(axis: Highcharts.AxisLabelsFormatterContextObject<any>) {
    const xAxisSetting = this.setting.options.xAxis;
    const value = this.numberFormatter.format(axis.value);
    if (xAxisSetting && xAxisSetting[0]) {
      return this.customAxisLabel(value, xAxisSetting[0].prefix, xAxisSetting[0].postfix);
    } else {
      return `<div>${value}</div>`;
    }
  }
}
