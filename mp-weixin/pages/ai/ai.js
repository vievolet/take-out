// AI 智能点餐页面（原生小程序语法，后端接口：POST /user/ai/recommend）
// 演示兜底：未登录（测试号无法走微信登录）时使用内置演示 token，保证页面可用
var baseUrl = 'http://localhost:8080';
var DEMO_TOKEN = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjEsImV4cCI6MTc5MzEyNDQyMn0.fASrSlrcPOWPn4v9TZBI4sz8gWKA7ZBOBe8M3GWqpgE';

function getToken() {
  return wx.getStorageSync('uni_id_token') || DEMO_TOKEN;
}

Page({
  data: {
    query: '',
    loading: false,
    result: null,
    spicyMap: ['不辣', '微辣', '中辣', '特辣']
  },

  onInput: function (e) {
    this.setData({ query: e.detail.value });
  },

  onExample: function (e) {
    this.setData({ query: e.currentTarget.dataset.text });
  },

  onRecommend: function () {
    var that = this;
    var query = (this.data.query || '').trim();
    if (!query) {
      wx.showToast({ title: '请先输入点餐需求', icon: 'none' });
      return;
    }
    this.setData({ loading: true, result: null });
    wx.request({
      url: baseUrl + '/user/ai/recommend',
      method: 'POST',
      header: {
        'Content-Type': 'application/json',
        'authentication': getToken()
      },
      data: { query: query },
      success: function (res) {
        if (res.data && res.data.code === 1) {
          var vo = res.data.data;
          vo.items = (vo.items || []).map(function (it) {
            it.spicyText = that.data.spicyMap[it.spicyLevel] || '未知';
            return it;
          });
          that.setData({ result: vo });
        } else {
          wx.showToast({ title: (res.data && res.data.msg) || '推荐失败', icon: 'none' });
        }
      },
      fail: function () {
        wx.showToast({ title: '请求失败，请确认后端已启动', icon: 'none' });
      },
      complete: function () {
        that.setData({ loading: false });
      }
    });
  },

  onAddCart: function (e) {
    var dishId = e.currentTarget.dataset.dishid;
    var setmealId = e.currentTarget.dataset.setmealid;
    var name = e.currentTarget.dataset.name;
    var body = dishId ? { dishId: dishId } : { setmealId: setmealId };
    wx.request({
      url: baseUrl + '/user/shoppingCart/add',
      method: 'POST',
      header: {
        'Content-Type': 'application/json',
        'authentication': getToken()
      },
      data: body,
      success: function (res) {
        if (res.data && res.data.code === 1) {
          wx.showToast({ title: name + ' 已加入购物车', icon: 'success' });
        } else {
          wx.showToast({ title: (res.data && res.data.msg) || '加入失败', icon: 'none' });
        }
      },
      fail: function () {
        wx.showToast({ title: '请求失败', icon: 'none' });
      }
    });
  },

  onAddAll: function () {
    var that = this;
    var items = (this.data.result && this.data.result.items) || [];
    if (!items.length) return;
    var done = 0;
    items.forEach(function (it) {
      var body = it.dishId ? { dishId: it.dishId } : { setmealId: it.setmealId };
      wx.request({
        url: baseUrl + '/user/shoppingCart/add',
        method: 'POST',
        header: {
          'Content-Type': 'application/json',
          'authentication': getToken()
        },
        data: body,
        success: function () {
          done++;
          if (done === items.length) {
            wx.showToast({ title: '已全部加入购物车', icon: 'success' });
          }
        },
        fail: function () {
          done++;
        }
      });
    });
  }
});
