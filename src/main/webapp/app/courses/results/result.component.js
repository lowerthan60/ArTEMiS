/**
 * Created by muenchdo on 11/06/16.
 */
(function () {
    'use strict';

    angular
        .module('artemisApp')
        .component('result', {
            bindings: {
                isQuiz: '<',
                participation: '<',
                showScore: '<',
                onNewResult: '&'
            },
            templateUrl: 'app/courses/results/result.html',
            controller: ResultController
        });

    ResultController.$inject = ['$http', '$uibModal', 'ParticipationResult', 'Repository', '$interval', '$scope', '$sce', 'JhiWebsocketService', 'Result'];

    function ResultController($http, $uibModal, ParticipationResult, Repository, $interval, $scope, $sce, JhiWebsocketService, Result) {
        var vm = this;

        vm.$onInit = init;
        vm.buildResultString = buildResultString;
        vm.hasResults = hasResults;
        vm.showDetails = showDetails;

        function init() {
            refresh(false);

            var websocketChannel = '/topic/participation/' + vm.participation.id + '/newResults';

            JhiWebsocketService.subscribe(websocketChannel);

            JhiWebsocketService.receive(websocketChannel).then(null, null, function (notify) {
                refresh(true);
            });

            $scope.$on('$destroy', function () {
                JhiWebsocketService.unsubscribe(websocketChannel);
            })
        }

        /**
         * refresh the participation and load the result if necessary
         *
         * @param forceLoad {boolean} force loading the result if the status is not QUEUED or BUILDING
         */
        function refresh(forceLoad) {
            if (vm.isQuiz) {
                // don't load status for quiz exercises
                refreshResult(forceLoad);
                return;
            } else if (!forceLoad && vm.participation.results && vm.participation.results.length > 0) {
                var result = vm.participation.results[0];
                if (result.successful) {
                    // don't load status on init for exercises with successful results
                    vm.results = vm.participation.results;
                    return;
                }
            }

            $http.get('api/participations/' + vm.participation.id + '/status', {
                ignoreLoadingBar: true
            }).then(function (response) {
                vm.queued = response.data === 'QUEUED';
                vm.building = response.data === 'BUILDING';
            }).finally(function () {
                if (!vm.queued && !vm.building) {
                    refreshResult(forceLoad);
                }
            });
        }

        function refreshResult(forceLoad) {
            if (forceLoad || !vm.participation.results) {
                // load results from server
                vm.results = ParticipationResult.query({
                    courseId: vm.participation.exercise.course.id,
                    exerciseId: vm.participation.exercise.id,
                    participationId: vm.participation.id,
                    showAllResults: false,
                    ratedOnly: vm.participation.exercise.type === "quiz"
                }, function (results) {
                    if (vm.onNewResult) {
                        vm.onNewResult({
                            $event: {
                                newResult: results[0]
                            }
                        });
                    }
                });
            } else {
                // take results from participation
                vm.results = vm.participation.results;
            }
        }

        function buildResultString(result) {
            if (result.resultString === 'No tests found') {
                return 'Build failed';
            } else {
                return result.resultString;
            }
        }

        function hasResults() {
            return !!vm.results && vm.results.length > 0;
        }

        function showDetails(result) {
            $uibModal.open({
                size: 'lg',
                templateUrl: 'app/courses/results/result-detail.html',
                controller: ['$http', 'result', function ($http, result) {
                    var vm = this;

                    vm.$onInit = init;

                    function init() {
                        vm.loading = true;
                        Result.details({
                            id: result.id
                        }, function (details) {
                            vm.details = details;
                            if (details.length == 0) {
                                Repository.buildlogs({
                                    participationId: result.participation.id
                                }, function (buildLogs) {
                                    _.forEach(buildLogs, function (buildLog) {
                                        buildLog.log = $sce.trustAsHtml(buildLog.log);
                                    });
                                    vm.buildLogs = buildLogs;
                                    vm.loading = false;
                                });
                            } else {
                                vm.loading = false;
                            }
                        });
                    }
                }],
                resolve: {
                    result: result
                },
                controllerAs: '$ctrl'
            });
        }
    }
})();
