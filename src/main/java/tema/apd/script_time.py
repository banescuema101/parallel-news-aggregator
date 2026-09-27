import numpy as np
import matplotlib.pyplot as plt

# Execution times recorded across 3 benchmark runs (in seconds)
real_time = {
    1:  [22.412, 22.376, 16.969],
    2:  [10.717, 10.554, 11.098],
    3:  [8.741,  8.780,  9.212],
    4:  [7.865,  7.910,  7.913],
    5:  [7.089,  7.528,  7.217],
    6:  [7.158,  7.005,  7.235],
    7:  [6.939,  7.092,  6.960],
    8:  [6.893,  6.864,  6.986],
    9:  [6.934,  6.979,  7.182],
    10: [6.878,  6.878,  6.842],
    20: [9.581,  8.596,  8.550]
}

# 1. Compute arithmetic mean for each thread count, then calculate Speedup and Efficiency
average_real_time = {p: float(np.mean(times)) for p, times in real_time.items()}

# Baseline sequential execution time (T1)
t1 = average_real_time[1]

# Speedup: S(p) = T(1) / T(p)
speedup = {p: t1 / average_real_time[p] for p in average_real_time}

# Efficiency: E(p) = S(p) / p
efficiency = {p: speedup[p] / p for p in speedup}

# Sort data points by thread count P
p_axis = sorted(average_real_time.keys())
avg_list = [average_real_time[p] for p in p_axis]
speedup_list = [speedup[p] for p in p_axis]
eff_list = [efficiency[p] for p in p_axis]

# 1. Execution Time Plot
plt.figure(figsize=(8, 5))
plt.plot(p_axis, avg_list, marker='o', color='#1f77b4', linewidth=2)
plt.xlabel("Number of Threads (P)")
plt.ylabel("Average Execution Time (s)")
plt.title("Execution Time vs. Number of Threads")
plt.grid(True)
plt.xticks(p_axis)
plt.tight_layout()
plt.savefig("execution_time.png", dpi=200)
plt.close()

# 2. Speedup Plot
plt.figure(figsize=(8, 5))
plt.plot(p_axis, speedup_list, marker='o', color='#ff7f0e', linewidth=2)
plt.xlabel("Number of Threads (P)")
plt.ylabel("Speedup S(P)")
plt.title("Speedup vs. Number of Threads")
plt.grid(True)
plt.xticks(p_axis)
plt.tight_layout()
plt.savefig("speedup.png", dpi=200)
plt.close()

# 3. Efficiency Plot
plt.figure(figsize=(8, 5))
plt.plot(p_axis, eff_list, marker='o', color='#2ca02c', linewidth=2)
plt.xlabel("Number of Threads (P)")
plt.ylabel("Efficiency E(P)")
plt.title("Efficiency vs. Number of Threads")
plt.grid(True)
plt.xticks(p_axis)
plt.tight_layout()
plt.savefig("efficiency.png", dpi=200)
plt.close()