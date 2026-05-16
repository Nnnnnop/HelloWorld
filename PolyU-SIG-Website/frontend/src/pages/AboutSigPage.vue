<template>
  <el-card class="about-card" shadow="never">
    <template #header>
      <div class="head">
        <span>About SIG</span>
      </div>
    </template>

    <div class="section">
      <h2>Who We Are</h2>
      <p>
        SIG Hub is the digital portal for PolyU Student Interest Group activities.
        We share student-led news and events, publish official announcements, and support
        self-study through a searchable resource platform.
      </p>
    </div>

    <div class="section">
      <h2>Core Functions</h2>
      <ul>
        <li>News & Events: updates for the PolyU student community.</li>
        <li>Announcement: official notices managed by administrators.</li>
        <li>Self-Study Materials: searchable learning resources with student/member access control.</li>
      </ul>
    </div>

    <div class="section">
      <h2>Community Values</h2>
      <p>
        We encourage collaborative learning, open communication, and responsible knowledge sharing.
        Public resources appear in Search for anyone; Member: Level1 resources require an approved SIG member account (either site
        Level 1 or 2) or an administrator; Member: Level2 resources require administrators or approved members designated as Level 2 on
        their account.
      </p>
    </div>
  </el-card>
</template>

<script setup>
</script>

<style scoped>
.about-card {
  width: 100%;
}

.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section + .section {
  margin-top: 18px;
}

h2 {
  margin: 0 0 8px 0;
  font-size: 18px;
}

p {
  margin: 0;
  line-height: 1.75;
  color: #4b5563;
}

ul {
  margin: 0;
  padding-left: 18px;
  color: #4b5563;
  line-height: 1.75;
}
</style>
