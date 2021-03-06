(function() {
    'use strict';

    angular
        .module('artemisApp')
        .controller('DropLocationDetailController', DropLocationDetailController);

    DropLocationDetailController.$inject = ['$scope', '$rootScope', '$stateParams', 'previousState', 'entity', 'DropLocation', 'DragAndDropQuestion'];

    function DropLocationDetailController($scope, $rootScope, $stateParams, previousState, entity, DropLocation, DragAndDropQuestion) {
        var vm = this;

        vm.dropLocation = entity;
        vm.previousState = previousState.name;

        var unsubscribe = $rootScope.$on('artemisApp:dropLocationUpdate', function(event, result) {
            vm.dropLocation = result;
        });
        $scope.$on('$destroy', unsubscribe);
    }
})();
