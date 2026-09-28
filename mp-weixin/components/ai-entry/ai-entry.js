// AI 点餐悬浮入口组件：不侵入编译后的首页 JS，仅作为原生组件注入首页模板
Component({
  methods: {
    goAi: function () {
      wx.navigateTo({ url: '/pages/ai/ai' });
    }
  }
});
