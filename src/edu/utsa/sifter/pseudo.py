#/**
# *
# * Sifter - Search Indexes for Text Evidence Relevantly
# *
# * Copyright (C) 2013, University of Texas at San Antonio (UTSA)
# *
# * Sifter is a digital forensics and e-discovery tool for conducting
# * text based string searches.  It clusters and ranks search hits
# * to improve investigative efficiency. Hit-level ranking uses a 
# * patent-pending ranking algorithm invented by Dr. Nicole Beebe at UTSA.
# *  
# * Licensed under the Apache License, Version 2.0 (the "License");
# * you may not use this file except in compliance with the License.
# * You may obtain a copy of the License at
# *
# *      http://www.apache.org/licenses/LICENSE-2.0
# *
# * Unless required by applicable law or agreed to in writing, software
# * distributed under the License is distributed on an "AS IS" BASIS,
# * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# * See the License for the specific language governing permissions and
# * limitations under the License.
# *
# * @author Jon Stewart, Lightbox Technologies
#**/


# each cell object contains its own dense vector
class Cell:
    DenseVector weights # corresponds to scalable weights, 'a', in paper
    double f
    double S2

Width = 50
Height = 50

Cells = new Cell[Height][Width] # 2-D array

# set random values for the SOM weights
for cell in Cells:
  # f starts at 1
  cell.f  = 1.0
  cell.s2 = 0
  for term in CorpusTerms: # all terms
    randWeight = random(0, 1)
    cell.weights[term] = randWeight
    # initial S2 value is sum of squared weights
    cell.s2 = cell.s2 + (randWeight * randWeight)


MaxIterations = 10

f = 1.0 # corresponds to paper
n = 0.1 # corresponds to paper

n_min  = 0.01
n_step = (n - n_min) / MaxIterations # how much we decrease n for each iteration (linear decrease)

r = 5 # initial neighborhood radius
r_step = r / MaxIterations # how much we decrease r for each iteration (linear decrease)

# main iteration loop
for i in [0, MaxIterations):

    # initial s2 calculation
    for cell in Cells:
        cell.s2 = 0
        for term in CorpusTerms:
            cell.s2 = cell.s2 + (cell.weights[term] * cell.weights[term])

    # document iteration loop
    rate = 1 - n # (1 - n) gets repeated in formulae, so convenient to set it here
    for doc in DocumentVectors:
        (x, y)    = findMinCellCoordinates(doc, f)
        neighbors = findNeighbors(x, y, floor(r)) # list of neighbor cells, including winner

        # update cell weights
        for cell in neighbors:
            nextF = rate * cell.f            # Rule 5
            adjustment = n / (rate * cell.f) # Rule 6
            sumSqrOld = 0.0                  # component of S2 update
            sumSqrNew = 0.0                  # component of S2 update
            for term in doc: # i.e., only non-zero terms
                # get the current weight
                weight    = cell.weights[term]

                # accumulate it in S'(t+1) component for S2 update
                sumSqrOld = sumSqrOld + ((weight * cell.f) * (weight * cell.f)) # S'(t+1)=Sum((a(t) * f(t))^2)

                # calculate the new weight from Rule 6
                weight    = adjustment + weight

                # accumulate new weight in S_2'(t+1) component for S2 update
                sumSqrNew = sumSqrNew + ((weight * nextF) * (weight * nextF))   # S_2'(t+1)=Sum((a(t+1) * f(t+1))^2)

                # save updated weight
                cell.weights[term] = weight

            # done with term loop, update other cell values
            cell.f = nextF # Rule 5
            cell.S2 = sumSqrNew + (rate * rate) * (cell.S2 - sumSqrOld) # S2(t+1) = S_2'(t+1) + (1 - n(t))^2 * (S2(t) - S'(t+1))

    r = r - r_step
    n = n - n_step

# end of SOM calculation



function findMinCellCoordinates(doc, f)
    minX = -1
    minY = -1

    # first, calculate s1 since it is constant for the doc
    # s1 is the cardinality of the doc terms
    s1 = 0
    for term in doc:
        s1 = s1 + 1

    minDistance = MAX_VALUE # set to infinity

    # iterate the cells
    for y in [0, Height):
        for x in [0, Width):
            cell = Cells[y][x] # current SOM cell

            s3 = 0
            # iterate terms in the doc
            for term in doc:
                # add the weight
                s3 = s3 + (f * cell.weights[term])

            s3 = -2 * s3

            dist = s1 + s2 + s3

            if dist < minDistance: # new minimum distance of cells so far
                minDistance = dist
                # leading candidate
                minX = x
                minY = y

    return (minX, minY)


function findNeighbors(x, y, r)
    # this is straightforward code to find cells around (x, y)
    minX = max(x - r, 0)
    minY = max(y - r, 0)
    maxX = min(x + r, Width - 1)
    maxY = min(y + r, Height - 1)

    neighbors = [] # empty list

    for curY in [minY, maxY]:
        for curX in [minX, maxX]:
            neighbors.append(Cells[curY][curX]) # add cell as a neighbor

    return neighbors
