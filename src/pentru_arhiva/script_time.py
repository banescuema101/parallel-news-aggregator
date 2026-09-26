import numpy as np
import matplotlib.pyplot as plt

# timpii mei masurati cu time

real_time = {
    1:  [22.412, 22.376, 16.969],
    2:  [10.717, 10.554, 11.098],
    3:  [8.741,  8.780,  9.212],
    4:  [7.865,  7.910,  7.913],
    5:  [7.089,  7.528, 7.217],
    6:  [7.158,  7.005, 7.235],
    7:  [6.939,  7.092, 6.960],
    8:  [6.893,  6.864, 6.986],
    9:  [6.934,  6.979, 7.182],
    10: [6.878,  6.878, 6.842],
    20: [9.581, 8.596, 8.550]
}

# Procesarea timpilor:
# 1. calculul mediei timpilor celor 3 rulari, apoi speedup-ul si eficienta.

averageRealTime = {}

for p in real_time:
    media = np.mean(real_time[p])
    averageRealTime[p] = media

# cat este T(1) deci media celor 3 rulari pt varianta secventiala, cu doar 1 thread.
T1 = averageRealTime[1]

# speedupul: T(1) / T(p)
speedup = {}
for p in averageRealTime:
    speedup[p] = T1 / averageRealTime[p]

# Eficienta:
efficiency = {}
for p in speedup:
    efficiency[p] = speedup[p] / p

# imi construiesc listele, in ordinea crescatoare a nr de threaduri, p.
P_axis = sorted(averageRealTime.keys())
avg_list = []
speedup_list = []
eff_list = []

for p in P_axis:
    avg_list.append(averageRealTime[p])
    speedup_list.append(speedup[p])
    eff_list.append(efficiency[p])

# Graficele cu matplotlib.

plt.figure(figsize=(8,5))
plt.plot(P_axis, avg_list, marker='o', color='red')
plt.xlabel("Numar thread-uri P")
plt.ylabel("Timp mediu executie (s)")
plt.title("Timp executie vs. numar de thread-uri")
plt.grid(True)
plt.xticks(P_axis)
plt.savefig("curba_timp.png", dpi=200)

# graficul pentru speedup:

plt.figure(figsize=(8,5))
plt.plot(P_axis, speedup_list, marker='o', color='orange')
plt.xlabel("Numar thread-uri P")
plt.ylabel("Speedup S(p)")
plt.title("Speedup vs. numar de thread-uri")
plt.grid(True)
plt.xticks(P_axis)
plt.savefig("curba_speedup.png", dpi=200)

# graficul eficientei:

plt.figure(figsize=(8,5))
plt.plot(P_axis, eff_list, marker='o', color='purple')
plt.xlabel("Număr thread-uri P")
plt.ylabel("Eficienta E(p)")
plt.title("Eficienta vs. numar de thread-uri")
plt.grid(True)
plt.xticks(P_axis)
plt.savefig("curba_eficienta.png", dpi=200)