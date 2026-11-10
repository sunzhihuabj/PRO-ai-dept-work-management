<script>
export default {
  onLaunch() {
    console.log('App Launch')
  }
}
</script>

<style>
page,
body {
  background-color: #f5f6fa;
  font-size: 28rpx;
  color: #303133;
}
.card {
  background: #ffffff;
  border-radius: 16rpx;
  margin: 20rpx;
  padding: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 21, 41, 0.06);
}
.title {
  font-size: 32rpx;
  font-weight: 600;
}
.sub {
  font-size: 24rpx;
  color: #909399;
}
.btn-primary {
  background: #409eff;
  color: #ffffff;
  border-radius: 12rpx;
  font-size: 30rpx;
}
.btn-block {
  width: 100%;
}
.tag {
  display: inline-block;
  padding: 4rpx 16rpx;
  border-radius: 8rpx;
  font-size: 22rpx;
}
.tag-warning { background: #fdf6ec; color: #e6a23c; }
.tag-success { background: #f0f9eb; color: #67c23a; }
.tag-danger { background: #fef0f0; color: #f56c6c; }
.tag-info { background: #f4f4f5; color: #909399; }
.tag-primary { background: #ecf5ff; color: #409eff; }
.empty {
  text-align: center;
  color: #909399;
  padding: 80rpx 0;
  font-size: 26rpx;
}
</style>
