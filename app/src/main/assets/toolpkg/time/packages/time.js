/* METADATA
{
  name: time

  display_name: {
    zh: "时间"
    en: "Time"
  }
  description: {
    zh: "按调用读取本机时间并格式化日期，不自动注入聊天上下文。"
    en: "Reads and formats local time when explicitly invoked."
  }
  enabledByDefault: true
  category: "Utility"
  tools: [
    {
      name: get_time
      description: {
        zh: "获取本次调用时的本机时间。"
        en: "Get the local time at invocation."
      }
      parameters: []
    },
    {
      name: format_time
      description: {
        zh: "返回本机当前日期，以及 24 小时制和 12 小时制时间。"
        en: "Return the current local date and time in 24-hour and 12-hour formats."
      }
      parameters: []
    }
  ]
}*/
const timePackage = (function () {
    async function get_time() {
        const now = new Date();
        return {
            timestamp: now.getTime(),
            iso: now.toISOString(),
            local: now.toLocaleString(),
            date: {
                year: now.getFullYear(),
                month: now.getMonth() + 1,
                day: now.getDate(),
                weekday: now.toLocaleDateString(undefined, { weekday: 'long' })
            },
            time: {
                hours: now.getHours(),
                minutes: now.getMinutes(),
                seconds: now.getSeconds()
            }
        };
    }
    async function format_time() {
        const now = new Date();
        const pad = (n) => n.toString().padStart(2, '0');
        const hours = now.getHours();
        const minutes = now.getMinutes();
        const seconds = now.getSeconds();
        const time24h = `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
        const suffix = hours >= 12 ? 'PM' : 'AM';
        const hour12 = hours % 12 === 0 ? 12 : hours % 12;
        const time12h = `${pad(hour12)}:${pad(minutes)}:${pad(seconds)} ${suffix}`;
        return {
            iso: now.toISOString(),
            date: `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`,
            time24h,
            time12h
        };
    }
    return {
        get_time,
        format_time
    };
})();
// 逐个导出
exports.get_time = timePackage.get_time;
exports.format_time = timePackage.format_time;
