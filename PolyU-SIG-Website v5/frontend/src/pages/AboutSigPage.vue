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
        All resource levels require login: public resources are available to any logged-in PolyU user, while member-only resources are limited to approved SIG members and administrators.
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
